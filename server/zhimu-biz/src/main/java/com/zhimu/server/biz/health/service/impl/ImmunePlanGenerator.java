package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.biz.health.entity.ImmunePlan;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.ImmunePlanMapper;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.query.ImmuneTemplateItemRow;
import com.zhimu.server.biz.health.query.ImmuneTemplateRow;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 免疫计划生成器（2.6.1）：按品种当前已发布模板生成按日龄项目；按日期项目在到达生成日时为在养批次生成。
 * <p>
 * 批次没有计划行时由 D01 / D02 懒生成（幂等：唯一索引 + 先查已有），这样不依赖模块04 的入栏事件也能联调；
 * 模块03 的防疫日常后续接入时同样调用本类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImmunePlanGenerator {

    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(18, 0);

    private final ImmunePlanMapper planMapper;
    private final HealthReferenceMapper referenceMapper;
    private final DrugCatalogQueryService drugCatalogQueryService;

    /** 品种有已发布的免疫模板 */
    public boolean hasTemplate(Long breedId) {
        return breedId != null && referenceMapper.selectPublishedTemplate(breedId) != null;
    }

    /** 批次没有任何计划行时按模板生成；已结束后不生成 */
    public void ensureGenerated(BatchInfo batch) {
        if (batch == null || batch.breedId() == null || !batch.active()) {
            return;
        }
        long existing = planMapper.selectCount(Wrappers.<ImmunePlan>lambdaQuery()
                .eq(ImmunePlan::getBatchId, batch.batchId()));
        if (existing > 0) {
            return;
        }
        ImmuneTemplateRow template = referenceMapper.selectPublishedTemplate(batch.breedId());
        if (template == null) {
            return;
        }
        List<ImmuneTemplateItemRow> items = referenceMapper.selectTemplateItems(template.getVersionId());
        Map<Long, FarmDrugRow> drugsByCatalog = drugsByCatalog();
        LocalDate today = BeijingTime.today();
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        for (ImmuneTemplateItemRow item : items) {
            LocalDate planDate = resolvePlanDate(item, batch, entryAge, today);
            if (planDate == null) {
                continue;
            }
            boolean preEntry = item.getDayAge() != null && item.getDayAge() < entryAge
                    && HealthCodes.PLAN_BY_DAY_AGE.equals(item.getScheduleType());
            ImmunePlan plan = new ImmunePlan();
            plan.setFarmId(batch.farmId());
            plan.setTargetType(HealthCodes.TARGET_BATCH);
            plan.setBatchId(batch.batchId());
            plan.setSource(HealthCodes.SOURCE_TEMPLATE);
            plan.setPreEntry(preEntry);
            plan.setTemplateId(template.getTemplateId());
            plan.setTemplateVersion(template.getVersionNo());
            plan.setTemplateItemId(item.getId());
            plan.setCatalogId(item.getVaccineId());
            FarmDrugRow drug = drugsByCatalog.get(item.getVaccineId());
            plan.setDrugId(drug == null ? null : drug.getId());
            plan.setVaccineName(vaccineName(item.getVaccineId(), drug));
            plan.setImmuneRound(item.getDoseRound());
            plan.setPlanMode(HealthCodes.PLAN_BY_DAY_AGE.equals(item.getScheduleType())
                    ? HealthCodes.PLAN_BY_DAY_AGE : HealthCodes.PLAN_BY_DATE);
            plan.setPlanAgeDay(item.getDayAge());
            plan.setOriginalPlanDate(planDate);
            plan.setPlanDate(planDate);
            plan.setDueTime(DEFAULT_DUE_TIME);
            plan.setDose(item.getDose());
            plan.setDoseUnit(item.getDoseUnit());
            plan.setMethodCode(item.getVaccinateMethodCode());
            plan.setStatus(preEntry ? HealthCodes.PLAN_PRE_ENTRY : HealthCodes.PLAN_PENDING);
            plan.setPostponed(false);
            plan.setManualDone(false);
            plan.setMissedNotified(false);
            plan.setVersion(0);
            plan.setCreatedBy(0L);
            try {
                planMapper.insert(plan);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                log.debug("免疫计划已由其他线程生成，跳过：batch={} item={}", batch.batchId(), item.getId());
            }
        }
    }

    /** 按日龄：入栏日期 + (计划日龄 - 入栏日龄)；按日期：今年计划日，到达生成日且不早于入栏日期 */
    private LocalDate resolvePlanDate(ImmuneTemplateItemRow item, BatchInfo batch, int entryAge, LocalDate today) {
        if (HealthCodes.PLAN_BY_DAY_AGE.equals(item.getScheduleType())) {
            if (item.getDayAge() == null) {
                return null;
            }
            return batch.entryDate().plusDays(item.getDayAge() - (long) entryAge);
        }
        if (item.getPlanMonth() == null || item.getPlanDay() == null) {
            return null;
        }
        int advance = item.getAdvanceDays() == null ? 0 : item.getAdvanceDays();
        LocalDate planDate = LocalDate.of(today.getYear(), item.getPlanMonth(), item.getPlanDay());
        if (planDate.isBefore(today.minusDays(advance)) || planDate.isBefore(batch.entryDate())) {
            return null;
        }
        return planDate;
    }

    private Map<Long, FarmDrugRow> drugsByCatalog() {
        Map<Long, FarmDrugRow> map = new HashMap<>();
        Long farmId = com.zhimu.server.common.context.FarmContext.farmId();
        if (farmId == null) {
            return map;
        }
        for (FarmDrugRow row : referenceMapper.selectDrugs(farmId)) {
            if (HealthCodes.CATEGORY_VACCINE.equals(row.getCategory()) && !map.containsKey(row.getCatalogId())) {
                map.put(row.getCatalogId(), row);
            }
        }
        return map;
    }

    private String vaccineName(Long catalogId, FarmDrugRow drug) {
        try {
            DrugCatalogInfo info = drugCatalogQueryService.requireCatalog(catalogId);
            return info.name();
        } catch (RuntimeException e) {
            return drug == null ? "疫苗" : drug.getName();
        }
    }
}
