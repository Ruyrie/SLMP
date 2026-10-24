package com.zhimu.server.admin.base.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.convert.DrugAdminConvert;import com.zhimu.server.admin.base.entity.DrugCatalog;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.mapper.BannedDrugMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogMapper;
import com.zhimu.server.admin.base.mapper.DrugCatalogUsageMapper;
import com.zhimu.server.admin.base.mapper.DrugLotRecallMapper;
import com.zhimu.server.admin.base.query.DrugCatalogQuery;
import com.zhimu.server.admin.base.request.DrugCatalogSaveRequest;
import com.zhimu.server.admin.base.request.DrugStatusRequest;
import com.zhimu.server.admin.base.service.DrugCatalogAdminService;
import com.zhimu.server.admin.base.support.BaseErrors;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.support.BaseTemplateSupport;
import com.zhimu.server.admin.base.support.DictTypes;
import com.zhimu.server.admin.base.support.DrugAdminSupport;
import com.zhimu.server.admin.base.vo.DrugCatalogDetailVO;
import com.zhimu.server.admin.base.vo.DrugCatalogRowVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ADM-D24–D28 兽药疫苗目录维护。 */
@Service
@RequiredArgsConstructor
public class DrugCatalogAdminServiceImpl implements DrugCatalogAdminService {

    private static final List<String> CATEGORY_ORDER = List.of("VET_DRUG", "VACCINE", "DISINFECTANT");

    private final DrugCatalogMapper catalogMapper;
    private final DrugCatalogUsageMapper usageMapper;
    private final BannedDrugMapper bannedDrugMapper;
    private final DrugLotRecallMapper recallMapper;
    private final BaseAdminReadMapper readMapper;
    private final BaseTemplateSupport support;
    private final DrugAdminSupport drugSupport;
    private final BaseLogs logs;

    @Override
    public PageVO<DrugCatalogRowVO> list(DrugCatalogQuery query) {
        String category = drugSupport.requireCategory(query.getDrugCategory());
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        String species = normalizeSpecies(query.getSpecies());
        String dosageForm = StringUtils.hasText(query.getDosageForm()) ? query.getDosageForm().trim() : null;
        String status = normalizeStatus(query.getStatus());
        List<DrugCatalog> catalogs = catalogMapper.selectList(Wrappers.<DrugCatalog>lambdaQuery()
                .eq(DrugCatalog::getDrugCategory, category)
                .eq(dosageForm != null, DrugCatalog::getDosageForm, dosageForm)
                .eq(status != null, DrugCatalog::getStatus, status)
                .and(keyword != null, wrapper -> wrapper.like(DrugCatalog::getName, keyword)
                        .or().like(DrugCatalog::getAlias, keyword))
                .orderByAsc(DrugCatalog::getSortNo)
                .orderByAsc(DrugCatalog::getId));
        Map<Long, List<DrugCatalogUsage>> usages = usagesByCatalog(
                catalogs.stream().map(DrugCatalog::getId).toList());
        if (species != null && !"DISINFECTANT".equals(category)) {
            List<DrugCatalog> filtered = new ArrayList<>();
            for (DrugCatalog catalog : catalogs) {
                for (DrugCatalogUsage usage : usages.getOrDefault(catalog.getId(), List.of())) {
                    if (species.equals(usage.getSpecies()) && Boolean.TRUE.equals(usage.getApplicable())) {
                        filtered.add(catalog);
                        break;
                    }
                }
            }
            catalogs = filtered;
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        long total = catalogs.size();
        int from = Math.min((page - 1) * size, catalogs.size());
        int to = Math.min(from + size, catalogs.size());
        List<DrugCatalogRowVO> rows = new ArrayList<>();
        Map<Long, String> adminNames = support.adminNames();
        Map<String, String> methodNames = drugSupport.dictNames(DictTypes.VACCINATE_METHOD);
        Map<String, String> disinfectNames = drugSupport.dictNames(DictTypes.DISINFECT_METHOD);
        for (DrugCatalog catalog : catalogs.subList(from, to)) {
            rows.add(DrugAdminConvert.row(catalog, usages.get(catalog.getId()),
                    readMapper.countArchivedFarmsByCatalog(catalog.getId()), methodNames, disinfectNames,
                    catalog.getUpdatedBy() == null ? null : adminNames.get(catalog.getUpdatedBy())));
        }
        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        for (String value : CATEGORY_ORDER) {
            tabCounts.put(value, Math.toIntExact(catalogMapper.selectCount(Wrappers.<DrugCatalog>lambdaQuery()
                    .eq(DrugCatalog::getDrugCategory, value))));
        }
        tabCounts.put("BANNED", Math.toIntExact(bannedDrugMapper.selectCount(Wrappers.lambdaQuery())));
        tabCounts.put("RECALL", Math.toIntExact(recallMapper.selectCount(Wrappers.lambdaQuery())));
        return PageVO.of(rows, total, page, size).withTabCounts(tabCounts);
    }

    @Override
    public DrugCatalogDetailVO detail(Long id) {
        return detail(drugSupport.requireCatalog(id));
    }

    @Override
    @Transactional
    public DrugCatalogDetailVO create(DrugCatalogSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        String category = drugSupport.requireCategory(request.getDrugCategory());
        drugSupport.validateBase(request, category);
        String name = request.getName().trim();
        drugSupport.assertNameNotBanned(name);
        requireNameUnique(category, name, null);
        List<DrugCatalogUsage> usages = drugSupport.usages(request, category);
        LocalDateTime now = BeijingTime.now();
        DrugCatalog catalog = new DrugCatalog();
        applyFields(catalog, request, category);
        catalog.setStatus("ENABLED");
        catalog.setBanned(false);
        catalog.setSortNo(nextSortNo());
        catalog.setLastChangeNote("新增目录项");
        catalog.setUpdatedBy(adminId);
        catalog.setVersion(0);
        catalogMapper.insert(catalog);
        for (DrugCatalogUsage usage : usages) {
            usage.setCatalogId(catalog.getId());
            usageMapper.insert(usage);
        }
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DRUG_CATALOG").opAction("CREATE").bizType("DRUG_CATALOG")
                .bizId(catalog.getId()).targetName(catalog.getName()).searchText(catalog.getName())
                .after(Map.of("drug_category", category, "name", catalog.getName()))
                .build());
        return detail(catalog);
    }

    @Override
    @Transactional
    public UpdateResult update(Long id, DrugCatalogSaveRequest request) {
        Long adminId = AdminContext.requireAdminId();
        DrugCatalog catalog = drugSupport.requireCatalog(id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), catalog.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String category = drugSupport.requireCategory(request.getDrugCategory());
        int archiveFarms = readMapper.countArchivedFarmsByCatalog(id);
        if (!category.equals(catalog.getDrugCategory()) && archiveFarms > 0) {
            throw BaseErrors.field("drug_category", "已有养殖场建档，不能修改类别");
        }
        drugSupport.validateBase(request, category);
        String name = request.getName().trim();
        drugSupport.assertNameNotBanned(name);
        requireNameUnique(category, name, id);
        List<DrugCatalogUsage> oldUsages = usages(id);
        List<DrugCatalogUsage> newUsages = drugSupport.usages(request, category);
        Map<String, Integer> oldWithdrawal = withdrawalBySpecies(oldUsages);
        Map<String, Integer> newWithdrawal = withdrawalBySpecies(newUsages);
        int changedSpecies = 0;
        for (String species : DrugAdminSupport.SPECIES) {
            if (!Objects.equals(oldWithdrawal.get(species), newWithdrawal.get(species))) {
                changedSpecies++;
            }
        }
        if (changedSpecies > 0 && !StringUtils.hasText(request.getChangeReason())) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_CHANGE);
        }
        applyFields(catalog, request, category);
        catalog.setLastChangeNote(StringUtils.hasText(request.getChangeReason())
                ? request.getChangeReason().trim() : "修改目录信息");
        catalog.setUpdatedBy(adminId);
        LocalDateTime now = BeijingTime.now();
        int rows = catalogMapper.update(null, Wrappers.<DrugCatalog>lambdaUpdate()
                .eq(DrugCatalog::getId, id)
                .eq(DrugCatalog::getVersion, catalog.getVersion())
                .set(DrugCatalog::getDrugCategory, catalog.getDrugCategory())
                .set(DrugCatalog::getName, catalog.getName())
                .set(DrugCatalog::getAlias, catalog.getAlias())
                .set(DrugCatalog::getDosageForm, catalog.getDosageForm())
                .set(DrugCatalog::getSpec, catalog.getSpec())
                .set(DrugCatalog::getMeasureUnit, catalog.getMeasureUnit())
                .set(DrugCatalog::getPackUnit, catalog.getPackUnit())
                .set(DrugCatalog::getPackSize, catalog.getPackSize())
                .set(DrugCatalog::getVaccineDoseUnit, catalog.getVaccineDoseUnit())
                .set(DrugCatalog::getDefaultVaccinateMethodCode, catalog.getDefaultVaccinateMethodCode())
                .set(DrugCatalog::getConcentration, catalog.getConcentration())
                .set(DrugCatalog::getDisinfectMethodCodes, catalog.getDisinfectMethodCodes())
                .set(DrugCatalog::getLastChangeNote, catalog.getLastChangeNote())
                .set(DrugCatalog::getUpdatedBy, adminId)
                .set(DrugCatalog::getUpdatedAt, now)
                .set(DrugCatalog::getVersion, catalog.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        usageMapper.delete(Wrappers.<DrugCatalogUsage>lambdaQuery()
                .eq(DrugCatalogUsage::getCatalogId, id));
        for (DrugCatalogUsage usage : newUsages) {
            usage.setCatalogId(id);
            usageMapper.insert(usage);
        }
        catalog.setVersion(catalog.getVersion() + 1);
        catalog.setUpdatedAt(now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DRUG_CATALOG").opAction("UPDATE").bizType("DRUG_CATALOG")
                .bizId(id).targetName(catalog.getName()).searchText(catalog.getName())
                .reason(StringUtils.hasText(request.getChangeReason()) ? request.getChangeReason().trim() : null)
                .before(Map.of("withdrawal_days", oldWithdrawal))
                .after(Map.of("withdrawal_days", newWithdrawal))
                .build());
        return new UpdateResult(detail(catalog), "已保存", changedSpecies);
    }

    @Override
    @Transactional
    public DrugCatalogDetailVO changeStatus(Long id, DrugStatusRequest request) {
        Long adminId = AdminContext.requireAdminId();
        DrugCatalog catalog = drugSupport.requireCatalog(id);
        if (request.getVersion() == null) {
            throw BaseErrors.field("version", "缺少版本号");
        }
        if (!Objects.equals(request.getVersion(), catalog.getVersion())) {
            throw BaseErrors.versionConflict();
        }
        String status = request.getStatus() == null ? null : request.getStatus().trim().toUpperCase();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BaseErrors.field("status", "请选择正确的状态");
        }
        if ("DISABLED".equals(status) && !StringUtils.hasText(request.getReason())) {
            throw BaseErrors.reasonEmpty(BaseErrors.MSG_REASON_DISABLE);
        }
        if ("ENABLED".equals(status)) {
            drugSupport.assertEnableAllowed(catalog);
        }
        String reason = "DISABLED".equals(status) ? request.getReason().trim() : null;
        LocalDateTime now = BeijingTime.now();
        int rows = catalogMapper.update(null, Wrappers.<DrugCatalog>lambdaUpdate()
                .eq(DrugCatalog::getId, id)
                .eq(DrugCatalog::getVersion, catalog.getVersion())
                .set(DrugCatalog::getStatus, status)
                .set(DrugCatalog::getDisableReason, reason)
                .set(DrugCatalog::getUpdatedBy, adminId)
                .set(DrugCatalog::getUpdatedAt, now)
                .set(DrugCatalog::getVersion, catalog.getVersion() + 1));
        if (rows == 0) {
            throw BaseErrors.versionConflict();
        }
        catalog.setStatus(status);
        catalog.setDisableReason(reason);
        catalog.setVersion(catalog.getVersion() + 1);
        catalog.setUpdatedAt(now);
        logs.record(OperationLogEntry.builder()
                .opType("BASE_DRUG_CATALOG").opAction("DISABLED".equals(status) ? "DISABLE" : "ENABLE")
                .bizType("DRUG_CATALOG").bizId(id).targetName(catalog.getName())
                .searchText(catalog.getName()).reason(reason)
                .build());
        return detail(catalog);
    }

    private DrugCatalogDetailVO detail(DrugCatalog catalog) {
        Map<Long, String> adminNames = support.adminNames();
        return DrugAdminConvert.detail(catalog, usages(catalog.getId()),
                readMapper.countArchivedFarmsByCatalog(catalog.getId()),
                readMapper.countActiveBatchesUsingCatalog(catalog.getId()),
                drugSupport.dictNames(DictTypes.VACCINATE_METHOD),
                drugSupport.dictNames(DictTypes.DISINFECT_METHOD),
                catalog.getUpdatedBy() == null ? null : adminNames.get(catalog.getUpdatedBy()));
    }

    private List<DrugCatalogUsage> usages(Long catalogId) {
        return usageMapper.selectList(Wrappers.<DrugCatalogUsage>lambdaQuery()
                .eq(DrugCatalogUsage::getCatalogId, catalogId));
    }

    private Map<Long, List<DrugCatalogUsage>> usagesByCatalog(List<Long> catalogIds) {
        Map<Long, List<DrugCatalogUsage>> map = new HashMap<>();
        if (catalogIds == null || catalogIds.isEmpty()) {
            return map;
        }
        for (DrugCatalogUsage usage : usageMapper.selectList(Wrappers.<DrugCatalogUsage>lambdaQuery()
                .in(DrugCatalogUsage::getCatalogId, catalogIds))) {
            map.computeIfAbsent(usage.getCatalogId(), key -> new ArrayList<>()).add(usage);
        }
        return map;
    }

    private void requireNameUnique(String category, String name, Long excludeId) {
        DrugCatalog existing = catalogMapper.selectOne(Wrappers.<DrugCatalog>lambdaQuery()
                .eq(DrugCatalog::getDrugCategory, category)
                .eq(DrugCatalog::getName, name)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw BaseErrors.duplicate("已有" + com.zhimu.server.admin.base.support.BaseAdminTexts
                    .drugCategory(category) + "“" + name + "”");
        }
    }

    private int nextSortNo() {
        DrugCatalog latest = catalogMapper.selectOne(Wrappers.<DrugCatalog>lambdaQuery()
                .orderByDesc(DrugCatalog::getSortNo).orderByDesc(DrugCatalog::getId).last("LIMIT 1"));
        return latest == null || latest.getSortNo() == null ? 1 : latest.getSortNo() + 1;
    }

    private static void applyFields(DrugCatalog catalog, DrugCatalogSaveRequest request, String category) {
        catalog.setDrugCategory(category);
        catalog.setName(request.getName().trim());
        catalog.setAlias(StringUtils.hasText(request.getAlias()) ? request.getAlias().trim() : null);
        catalog.setDosageForm(request.getDosageForm().trim());
        catalog.setSpec(request.getSpec().trim());
        catalog.setMeasureUnit(request.getMeasureUnit().trim().toUpperCase());
        catalog.setPackUnit(request.getPackUnit().trim());
        catalog.setPackSize(request.getPackSize());
        if ("VACCINE".equals(category)) {
            catalog.setVaccineDoseUnit(request.getVaccineDoseUnit().trim().toUpperCase());
            catalog.setDefaultVaccinateMethodCode(StringUtils.hasText(request.getDefaultVaccinateMethodCode())
                    ? request.getDefaultVaccinateMethodCode().trim().toUpperCase() : null);
        } else {
            catalog.setVaccineDoseUnit(null);
            catalog.setDefaultVaccinateMethodCode(null);
        }
        if ("DISINFECTANT".equals(category)) {
            catalog.setConcentration(StringUtils.hasText(request.getConcentration())
                    ? request.getConcentration().trim() : null);
            catalog.setDisinfectMethodCodes(request.getDisinfectMethodCodes() == null
                    || request.getDisinfectMethodCodes().isEmpty() ? null
                    : String.join(",", request.getDisinfectMethodCodes().stream()
                    .map(String::trim).filter(StringUtils::hasText).toList()));
        } else {
            catalog.setConcentration(null);
            catalog.setDisinfectMethodCodes(null);
        }
    }

    private static Map<String, Integer> withdrawalBySpecies(List<DrugCatalogUsage> usages) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (DrugCatalogUsage usage : usages) {
            map.put(usage.getSpecies(), usage.getWithdrawalDays());
        }
        return map;
    }

    private static String normalizeSpecies(String species) {
        return StringUtils.hasText(species) ? species.trim().toUpperCase() : null;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String value = status.trim().toUpperCase();
        if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) {
            throw BaseErrors.field("status", "请选择正确的状态");
        }
        return value;
    }
}
