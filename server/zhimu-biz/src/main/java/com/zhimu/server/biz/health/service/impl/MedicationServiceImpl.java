package com.zhimu.server.biz.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.admin.base.api.DrugCatalogInfo;
import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.admin.base.vo.BannedDrugHitVO;
import com.zhimu.server.biz.health.entity.BatchWithdrawal;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.health.query.BatchPenStockRow;
import com.zhimu.server.biz.health.query.DrugStockRow;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.query.FarmMemberRow;
import com.zhimu.server.biz.health.query.MedicationDrugOptionQuery;
import com.zhimu.server.biz.health.query.MedicationTargetQuery;
import com.zhimu.server.biz.health.request.MedicationCreateRequest;
import com.zhimu.server.biz.health.request.MedicationPreviewRequest;
import com.zhimu.server.biz.health.request.MedicationUpdateRequest;
import com.zhimu.server.biz.health.request.NotifyManagerRequest;
import com.zhimu.server.biz.health.service.MedicationService;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthCodes;
import com.zhimu.server.biz.health.support.HealthFormat;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.health.support.MedicationLogSnapshot;
import com.zhimu.server.biz.health.vo.DeleteImpactVO;
import com.zhimu.server.biz.health.vo.MedicationCreateResultVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionVO;
import com.zhimu.server.biz.health.vo.MedicationDrugOptionsVO;
import com.zhimu.server.biz.health.vo.MedicationRecordVO;
import com.zhimu.server.biz.health.vo.MedicationTargetVO;
import com.zhimu.server.biz.health.vo.NotifyResultVO;
import com.zhimu.server.biz.health.vo.ReleaseDatePreviewVO;
import com.zhimu.server.biz.health.vo.ToastVO;
import com.zhimu.server.biz.health.vo.WithdrawalDrugVO;
import com.zhimu.server.biz.health.vo.WithdrawalVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockCommand;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.enums.Species;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** API-D17–D27 用药与休药实现：禁用药 42203、批号 42204、结存 42201、休药重算与通知场长。 */
@Service
@RequiredArgsConstructor
public class MedicationServiceImpl implements MedicationService {

    private static final DateTimeFormatter GROUP_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final MedicationRecordMapper recordMapper;
    private final BatchWithdrawalMapper withdrawalMapper;
    private final HealthReferenceMapper referenceMapper;
    private final HealthSupport support;
    private final HealthAudit audit;
    private final DrugStockPort drugStockPort;
    private final DrugCatalogQueryService drugCatalogQueryService;
    private final com.zhimu.server.biz.herd.api.BatchQueryService batchQueryService;
    private final MessageService messageService;
    private final StringRedisTemplate stringRedisTemplate;

    // ===== API-D17 休药状态 =====

    @Override
    public WithdrawalVO withdrawal(Long batchId) {
        BatchInfo batch = support.requireBatch(batchId);
        support.checkBatchAccess(batch);
        List<MedicationRecord> records = effective(batchId);
        Long mainPenId = support.mainPenId(batch);
        WithdrawalVO vo = new WithdrawalVO();
        vo.setBatchId(batchId);
        vo.setBatchNo(batch.batchNo());
        vo.setPenName(support.penName(mainPenId));
        vo.setCurrentCount(batch.currentCount() == null ? 0 : batch.currentCount());
        LocalDate today = BeijingTime.today();
        LocalDate release = null;
        MedicationRecord decisive = null;
        Map<Long, List<MedicationRecord>> byCatalog = new LinkedHashMap<>();
        for (MedicationRecord record : records) {
            if (record.getWithdrawalEndDate() == null) {
                continue;
            }
            byCatalog.computeIfAbsent(record.getCatalogId(), key -> new ArrayList<>()).add(record);
            if (release == null || record.getWithdrawalEndDate().isAfter(release)) {
                release = record.getWithdrawalEndDate();
                decisive = record;
            }
        }
        if (release == null) {
            vo.setStatus(HealthCodes.WITHDRAWAL_NONE);
            vo.setDrugs(List.of());
        } else {
            boolean inWithdrawal = today.isBefore(release);
            vo.setStatus(inWithdrawal ? HealthCodes.WITHDRAWAL_IN : HealthCodes.WITHDRAWAL_ENDED);
            vo.setReleaseDate(release);
            vo.setDaysLeft(inWithdrawal ? (int) java.time.temporal.ChronoUnit.DAYS.between(today, release) : 0);
            vo.setDecisiveDrugName(decisive.getDrugName());
            vo.setLastMedDate(decisive.getLastDate());
            vo.setWithdrawalDays(decisive.getWithdrawalDays());
            vo.setProgress(progress(decisive, today));
            List<WithdrawalDrugVO> drugs = new ArrayList<>();
            for (List<MedicationRecord> group : byCatalog.values()) {
                MedicationRecord latest = group.stream()
                        .max(Comparator.comparing(MedicationRecord::getWithdrawalEndDate)
                                .thenComparing(MedicationRecord::getId))
                        .orElse(group.get(0));
                WithdrawalDrugVO drug = new WithdrawalDrugVO();
                drug.setCatalogId(latest.getCatalogId());
                drug.setDrugName(latest.getDrugName());
                drug.setLastDate(latest.getLastDate());
                drug.setWithdrawalDays(latest.getWithdrawalDays());
                drug.setEndDate(latest.getWithdrawalEndDate());
                boolean isDecisive = latest.getWithdrawalEndDate().equals(release);
                String tag;
                if (!latest.getWithdrawalEndDate().isAfter(today)) {
                    tag = "ENDED";
                } else if (isDecisive) {
                    tag = "LATEST";
                } else {
                    tag = HealthCodes.WITHDRAWAL_NONE;
                }
                drug.setTag(tag);
                drug.setTagText(HealthCodes.withdrawalTagText(tag));
                drugs.add(drug);
            }
            drugs.sort(Comparator.comparing(WithdrawalDrugVO::getEndDate).reversed());
            vo.setDrugs(drugs);
            if (release != null) {
                List<LocalDate> remind = List.of(release.minusDays(3), release);
                vo.setRemindDates(remind);
                vo.setRemindText(remindText(remind, release));
            }
        }
        vo.setStatusText(HealthCodes.withdrawalStatusText(vo.getStatus()));
        return vo;
    }

    private String remindText(List<LocalDate> dates, LocalDate release) {
        String names = managerNamesText();
        String first = HealthFormat.dateText(dates.get(0));
        String second = HealthFormat.dateText(release);
        String firstText = "今天".equals(first) ? "" : first;
        return firstText + "和 " + second + "各提醒你" + (names == null ? "" : "和" + names) + "一次。";
    }

    private String managerNamesText() {
        List<String> names = new ArrayList<>();
        for (FarmMemberRow row : support.members()) {
            if ("MANAGER".equals(row.getRole()) && row.getName() != null) {
                names.add(row.getName());
            }
        }
        return names.isEmpty() ? null : String.join("、", names);
    }

    private Double progress(MedicationRecord record, LocalDate today) {
        if (record.getWithdrawalDays() == null || record.getWithdrawalDays() <= 0) {
            return 1.0;
        }
        if (record.getLastDate() == null) {
            return 0.0;
        }
        long elapsed = java.time.temporal.ChronoUnit.DAYS.between(record.getLastDate(), today);
        double value = (double) elapsed / record.getWithdrawalDays();
        return Math.max(0.0, Math.min(1.0, value));
    }

    // ===== API-D18 用药记录 =====

    @Override
    public PageVO<MedicationRecordVO> records(Long batchId, PageQuery query) {
        BatchInfo batch = support.requireBatch(batchId);
        support.checkBatchAccess(batch);
        int pageNo = query == null ? 1 : query.pageOrDefault();
        int size = query == null ? 20 : query.sizeOrDefault();
        Page<MedicationRecord> result = recordMapper.selectPage(new Page<>(pageNo, size),
                Wrappers.<MedicationRecord>lambdaQuery()
                        .eq(MedicationRecord::getBatchId, batchId)
                        .orderByDesc(MedicationRecord::getFirstDate)
                        .orderByDesc(MedicationRecord::getId));
        List<MedicationRecordVO> list = new ArrayList<>();
        for (MedicationRecord record : result.getRecords()) {
            list.add(toVO(record, batch, false));
        }
        return PageVO.of(list, result.getTotal(), pageNo, size);
    }

    // ===== API-D19 用药对象 =====

    @Override
    public List<MedicationTargetVO> targets(MedicationTargetQuery query) {
        String targetType = query == null || query.getTargetType() == null ? ""
                : query.getTargetType().toUpperCase();
        String keyword = query == null || query.getKeyword() == null ? null : query.getKeyword().trim();
        Long farmId = support.farmId();
        List<MedicationTargetVO> list = new ArrayList<>();
        if (HealthCodes.MED_TARGET_BATCH.equals(targetType)) {
            for (BatchInfo batch : activeBatches()) {
                Long penId = support.mainPenId(batch);
                if (support.staffView() && !support.member().canAccessPen(penId)) {
                    continue;
                }
                String penName = support.penName(penId);
                if (keyword != null && !keyword.isEmpty() && !contains(batch.batchNo(), keyword)
                        && !contains(penName, keyword)) {
                    continue;
                }
                MedicationTargetVO vo = new MedicationTargetVO();
                vo.setBatchId(batch.batchId());
                vo.setBatchNo(batch.batchNo());
                vo.setPenId(penId);
                vo.setPenName(penName);
                vo.setCurrentCount(batch.currentCount());
                vo.setCategoryId(batch.categoryId());
                list.add(vo);
            }
            return list;
        }
        if (HealthCodes.MED_TARGET_PEN.equals(targetType)) {
            Map<Long, List<BatchPenStockRow>> byPen = new LinkedHashMap<>();
            for (BatchPenStockRow row : support.activeBatchPens()) {
                byPen.computeIfAbsent(row.getPenId(), key -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<Long, List<BatchPenStockRow>> entry : byPen.entrySet()) {
                if (support.staffView() && !support.member().canAccessPen(entry.getKey())) {
                    continue;
                }
                String penName = support.penName(entry.getKey());
                if (keyword != null && !keyword.isEmpty() && !contains(penName, keyword)) {
                    continue;
                }
                MedicationTargetVO vo = new MedicationTargetVO();
                vo.setPenId(entry.getKey());
                vo.setPenName(penName);
                vo.setHasBatch(true);
                for (BatchPenStockRow row : entry.getValue()) {
                    MedicationTargetVO.BatchItem item = new MedicationTargetVO.BatchItem();
                    item.setBatchId(row.getBatchId());
                    item.setBatchNo(support.batchNo(row.getBatchId()));
                    item.setStockCount(row.getStockCount());
                    vo.getBatches().add(item);
                    vo.setCurrentCount((vo.getCurrentCount() == null ? 0 : vo.getCurrentCount())
                            + (row.getStockCount() == null ? 0 : row.getStockCount()));
                }
                list.add(vo);
            }
            return list;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "请选择用药对象");
    }

    // ===== API-D20 可选兽药 =====

    @Override
    public MedicationDrugOptionsVO drugOptions(MedicationDrugOptionQuery query) {
        if (query == null || (query.getBatchId() == null && query.getPenId() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次或栏舍");
        }
        String species = resolveSpecies(query.getBatchId(), query.getPenId());
        MedicationDrugOptionsVO result = new MedicationDrugOptionsVO();
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            BannedDrugHitVO hit = drugCatalogQueryService.checkBanned(keyword);
            if (hit != null) {
                result.setBannedHint(hit.getName() + "为禁用药，不能使用");
                return result;
            }
        }
        Map<Long, BigDecimal> stock = stockByDrug();
        for (FarmDrugRow row : drugRows().values()) {
            if (!HealthCodes.CATEGORY_VET_DRUG.equals(row.getCategory())) {
                continue;
            }
            if (keyword != null && !keyword.isEmpty() && !contains(row.getName(), keyword)) {
                continue;
            }
            DrugCatalogInfo catalog;
            try {
                catalog = drugCatalogQueryService.requireCatalog(row.getCatalogId());
            } catch (RuntimeException e) {
                continue;
            }
            if (drugCatalogQueryService.isBanned(row.getCatalogId(), species)) {
                continue;
            }
            Usage usage = usage(row.getCatalogId(), species);
            BigDecimal qty = stock.getOrDefault(row.getId(), BigDecimal.ZERO);
            if (qty.signum() <= 0) {
                continue;
            }
            MedicationDrugOptionVO vo = new MedicationDrugOptionVO();
            vo.setDrugId(row.getId());
            vo.setCatalogId(row.getCatalogId());
            vo.setDrugName(catalog.name());
            vo.setSpec(row.getSpec() == null ? catalog.spec() : row.getSpec());
            vo.setStockUnit(HealthCodes.unitText(row.getUnit()));
            vo.setAvailableQty(HealthFormat.plain(qty));
            vo.setRouteCode(usage.routeCode());
            vo.setRouteName(HealthCodes.routeText(usage.routeCode()));
            vo.setRecommendedDose(usage.recommendedDose());
            vo.setDoseUnit(HealthCodes.drugDoseUnitText(usage.doseUnit()));
            vo.setRecommendedCourseDays(usage.courseDays());
            vo.setWithdrawalDays(usage.withdrawalDays());
            vo.setApplicable(usage.applicable());
            String speciesText = Species.valueOf(species).getText();
            vo.setHintText("休药 " + usage.withdrawalDays() + " 天 · "
                    + (usage.applicable() ? "禁用药不可选" : "不适用于" + speciesText));
            result.getList().add(vo);
        }
        return result;
    }

    // ===== API-D21 预估 =====

    @Override
    public ReleaseDatePreviewVO preview(MedicationPreviewRequest request) {
        ReleaseDatePreviewVO vo = new ReleaseDatePreviewVO();
        vo.setReady(false);
        if (request == null || request.getDrugId() == null || request.getCourseDays() == null
                || request.getCourseDays() < 1) {
            return vo;
        }
        if (request.getBatchId() == null && request.getPenId() == null) {
            return vo;
        }
        LocalDate firstDate = request.getFirstDate() == null ? BeijingTime.today() : request.getFirstDate();
        FarmDrugRow drug = drugRows().get(request.getDrugId());
        if (drug == null) {
            return vo;
        }
        String species = resolveSpecies(request.getBatchId(), request.getPenId());
        Usage usage = usage(drug.getCatalogId(), species);
        LocalDate lastDate = firstDate.plusDays(request.getCourseDays() - 1L);
        LocalDate release = lastDate.plusDays(usage.withdrawalDays());
        vo.setReady(true);
        vo.setLastDate(lastDate);
        vo.setReleaseDate(release);
        vo.setReleaseText(HealthFormat.dateWithWeek(release));
        vo.setUsedUnit(HealthCodes.unitText(drug.getUnit()));
        vo.setOverDose(request.getDose() != null && usage.recommendedDose() != null
                && request.getDose().compareTo(usage.recommendedDose()) > 0);
        vo.setCategoryMismatch(!usage.applicable());
        for (BatchInfo batch : previewBatches(request)) {
            ReleaseDatePreviewVO.PerBatch perBatch = new ReleaseDatePreviewVO.PerBatch();
            perBatch.setBatchId(batch.batchId());
            perBatch.setBatchNo(batch.batchNo());
            LocalDate batchRelease = release;
            for (MedicationRecord existing : effective(batch.batchId())) {
                if (existing.getWithdrawalEndDate() != null
                        && existing.getWithdrawalEndDate().isAfter(batchRelease)) {
                    batchRelease = existing.getWithdrawalEndDate();
                }
            }
            perBatch.setReleaseDate(batchRelease);
            perBatch.setEstimatedQty(estimate(usage, request.getDose(), batch.currentCount(),
                    request.getCourseDays(), drug.getUnit()));
            vo.getPerBatch().add(perBatch);
            if (batchRelease.isAfter(release)) {
                vo.setReleaseDate(batchRelease);
                vo.setReleaseText(HealthFormat.dateWithWeek(batchRelease));
            }
        }
        return vo;
    }

    private List<BatchInfo> previewBatches(MedicationPreviewRequest request) {
        if (request.getBatchId() != null) {
            return List.of(support.requireBatch(request.getBatchId()));
        }
        List<BatchInfo> batches = new ArrayList<>();
        for (BatchPenStockRow row : support.activeBatchesOfPen(request.getPenId())) {
            batches.add(support.requireBatch(row.getBatchId()));
        }
        return batches;
    }

    // ===== API-D22 登记用药 =====

    @Override
    @Transactional
    public MedicationCreateResultVO create(MedicationCreateRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写用药信息");
        }
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().toUpperCase();
        if (!HealthCodes.MED_TARGET_BATCH.equals(targetType) && !HealthCodes.MED_TARGET_PEN.equals(targetType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    HealthCodes.MED_TARGET_BATCH.equals(targetType) ? "请选择批次" : "请选择用药对象");
        }
        boolean byBatch = HealthCodes.MED_TARGET_BATCH.equals(targetType);
        if (byBatch && request.getBatchId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择批次");
        }
        if (!byBatch && request.getPenId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择栏舍");
        }
        List<BatchPenStockRow> batchPens;
        if (byBatch) {
            BatchInfo batch = support.requireActiveBatch(request.getBatchId());
            support.checkBatchAccess(batch);
            BatchPenStockRow row = new BatchPenStockRow();
            row.setBatchId(batch.batchId());
            row.setPenId(support.mainPenId(batch));
            row.setStockCount(batch.currentCount());
            batchPens = List.of(row);
        } else {
            support.checkPenAccess(request.getPenId());
            batchPens = support.activeBatchesOfPen(request.getPenId());
            if (batchPens.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        support.penName(request.getPenId()) + "没有在栏批次，不能登记用药");
            }
        }
        if (request.getDrugId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择药品");
        }
        FarmDrugRow drug = drugRows().get(request.getDrugId());
        if (drug == null || !HealthCodes.CATEGORY_VET_DRUG.equals(drug.getCategory())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择药品");
        }
        Long firstBatchId = batchPens.get(0).getBatchId();
        BatchInfo firstBatch = support.requireBatch(firstBatchId);
        String species = support.species(firstBatch.categoryId());
        drugCatalogQueryService.assertNotBanned(drug.getCatalogId(), species);
        if (request.getCourseDays() == null || request.getCourseDays() < 1 || request.getCourseDays() > 90) {
            throw new BizException(ErrorCode.PARAM_INVALID, "疗程至少 1 天");
        }
        LocalDate firstDate = request.getFirstDate() == null ? BeijingTime.today() : request.getFirstDate();
        if (firstDate.isAfter(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首次用药日期不能晚于今天");
        }
        if (firstDate.isBefore(firstBatch.entryDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首次用药日期不能早于批次入栏日期");
        }
        if (request.getDose() == null || request.getDose().signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "剂量需大于 0");
        }
        Usage usage = usage(drug.getCatalogId(), species);
        boolean overDose = usage.recommendedDose() != null
                && request.getDose().compareTo(usage.recommendedDose()) > 0;
        boolean mismatch = !usage.applicable();
        if (overDose && (request.getOverDoseNote() == null || request.getOverDoseNote().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写超量说明");
        }
        if (mismatch && (request.getUsageNote() == null || request.getUsageNote().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写用药说明");
        }
        if (request.getReason() == null || request.getReason().isBlank() || request.getReason().length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写用药原因");
        }
        BigDecimal usedQty = request.getUsedQty();
        if (usedQty == null || usedQty.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写实际用量");
        }
        List<DrugLotOption> lots = support.lotsFor(drug.getId(), HealthCodes.PURPOSE_MEDICATE);
        DrugLotOption lot = support.findLot(lots, request.getDrugLotId());
        support.assertLotUsable(lot, usedQty, drug.getName(), HealthCodes.unitText(drug.getUnit()));

        String clientRequestId = clientRequestId();
        MedicationCreateResultVO existing = existingResult(clientRequestId, batchPens);
        if (existing != null) {
            return existing;
        }
        String groupNo = nextGroupNo();
        LocalDate lastDate = firstDate.plusDays(request.getCourseDays() - 1L);
        LocalDate release = lastDate.plusDays(usage.withdrawalDays());
        Long executorId = executor(request.getExecutorId(), batchPens.get(0).getPenId());
        BigDecimal remaining = usedQty;
        int totalStock = batchPens.stream().mapToInt(row -> row.getStockCount() == null ? 0 : row.getStockCount())
                .sum();
        MedicationCreateResultVO result = new MedicationCreateResultVO();
        result.setGroupNo(groupNo);
        for (int i = 0; i < batchPens.size(); i++) {
            BatchPenStockRow row = batchPens.get(i);
            BigDecimal share;
            if (i == batchPens.size() - 1) {
                share = remaining;
            } else if (totalStock <= 0) {
                share = usedQty.divide(BigDecimal.valueOf(batchPens.size()), 3, RoundingMode.HALF_UP);
            } else {
                share = usedQty.multiply(BigDecimal.valueOf(row.getStockCount() == null ? 0 : row.getStockCount()))
                        .divide(BigDecimal.valueOf(totalStock), 3, RoundingMode.HALF_UP);
            }
            remaining = remaining.subtract(share);
            MedicationRecord record = new MedicationRecord();
            record.setFarmId(support.farmId());
            record.setGroupNo(groupNo);
            record.setTargetType(targetType);
            record.setPenId(row.getPenId());
            record.setBatchId(row.getBatchId());
            record.setCatalogId(drug.getCatalogId());
            record.setDrugId(drug.getId());
            record.setDrugName(drug.getName());
            record.setDrugLotId(lot.lotId());
            record.setLotNo(lot.lotNo());
            record.setLotExpireDate(lot.expireDate());
            record.setDose(request.getDose());
            record.setDoseUnit(usage.doseUnit());
            record.setRecommendedDose(usage.recommendedDose());
            record.setRouteCode(usage.routeCode());
            record.setCourseDays(request.getCourseDays());
            record.setFirstDate(firstDate);
            record.setLastDate(lastDate);
            record.setWithdrawalDays(usage.withdrawalDays());
            record.setWithdrawalEndDate(lastDate.plusDays(usage.withdrawalDays()));
            record.setOverDose(overDose);
            record.setOverDoseNote(overDose ? request.getOverDoseNote() : null);
            record.setCategoryMismatch(mismatch);
            record.setUsageNote(mismatch ? request.getUsageNote() : null);
            record.setReason(request.getReason());
            record.setEstimatedQty(estimate(usage, request.getDose(), row.getStockCount(),
                    request.getCourseDays(), drug.getUnit()));
            record.setUsedQty(share);
            record.setUsedUnit(drug.getUnit());
            record.setReportId(request.getReportId());
            record.setDiagnosisId(request.getDiagnosisId());
            record.setExecutorId(executorId);
            record.setClientRequestId(clientRequestId);
            record.setDeleted(false);
            record.setVersion(0);
            recordMapper.insert(record);
            StockConsumeResult consume = drugStockPort.deduct(new DrugStockCommand(
                    record.getFarmId(), record.getDrugId(), lot.lotId(), share,
                    DrugStockCommand.FLOW_MEDICATION_USE, DrugStockCommand.BIZ_MEDICATION_RECORD, record.getId(),
                    record.getBatchId(), record.getPenId(), executorId, null, firstDate, drug.getName()));
            recordMapper.update(null, Wrappers.<MedicationRecord>lambdaUpdate()
                    .eq(MedicationRecord::getId, record.getId())
                    .set(MedicationRecord::getStockFlowId, consume.flowId())
                    .set(MedicationRecord::getUnitPrice, consume.unitPrice())
                    .set(MedicationRecord::getCostAmount, consume.amount()));
            LocalDate batchRelease = recalculate(record.getBatchId());
            result.getRecordIds().add(record.getId());
            MedicationCreateResultVO.PerBatch perBatch = new MedicationCreateResultVO.PerBatch();
            perBatch.setBatchNo(support.batchNo(record.getBatchId()));
            perBatch.setReleaseDate(batchRelease);
            result.getPerBatch().add(perBatch);
            record.setStockFlowId(consume.flowId());
            record.setUnitPrice(consume.unitPrice());
            record.setCostAmount(consume.amount());
            audit.record(record.getFarmId(), "MEDICATION_RECORD", record.getId(), "CREATE",
                    record.getDrugName(), support.batchNo(record.getBatchId()), null,
                    MedicationLogSnapshot.of(record, support.userName(record.getExecutorId())));
        }
        LocalDate latest = result.getPerBatch().stream().map(MedicationCreateResultVO.PerBatch::getReleaseDate)
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(release);
        result.setToast("已登记用药，可出栏日期为 " + HealthFormat.dateWithWeek(latest));
        return result;
    }

    // ===== API-D23 详情 =====

    @Override
    public MedicationRecordVO detail(Long recordId) {
        MedicationRecord record = requireRecord(recordId);
        checkRecordAccess(record);
        BatchInfo batch = support.requireBatch(record.getBatchId());
        return toVO(record, batch, true);
    }

    // ===== API-D24 修改 =====

    @Override
    @Transactional
    public MedicationRecordVO update(Long recordId, MedicationUpdateRequest request) {
        MedicationRecord record = requireRecord(recordId);
        checkRecordAccess(record);
        if (request == null || request.getVersion() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        BatchInfo batch = support.requireBatch(record.getBatchId());
        boolean locked = !batch.active();
        if (locked) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, "记录已锁定，请提交修改申请")
                    .with("reason", "LOCKED");
        }
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        if (support.staffView() && !mine) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能修改自己登记的记录");
        }
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        if (!sameDay) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    support.staffView() ? "用药记录只能在登记当天修改" : "已超过可直接修改的时间，请提交修改申请")
                    .with("reason", "EXPIRED");
        }
        FarmDrugRow drug = request.getDrugId() == null ? drugRows().get(record.getDrugId())
                : drugRows().get(request.getDrugId());
        if (drug == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择药品");
        }
        String species = support.species(batch.categoryId());
        drugCatalogQueryService.assertNotBanned(drug.getCatalogId(), species);
        Usage usage = usage(drug.getCatalogId(), species);
        BigDecimal dose = request.getDose() == null ? record.getDose() : request.getDose();
        int courseDays = request.getCourseDays() == null ? record.getCourseDays() : request.getCourseDays();
        LocalDate firstDate = request.getFirstDate() == null ? record.getFirstDate() : request.getFirstDate();
        BigDecimal usedQty = request.getUsedQty() == null ? record.getUsedQty() : request.getUsedQty();
        if (courseDays < 1 || courseDays > 90) {
            throw new BizException(ErrorCode.PARAM_INVALID, "疗程至少 1 天");
        }
        if (firstDate.isAfter(BeijingTime.today())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "首次用药日期不能晚于今天");
        }
        if (usedQty.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写实际用量");
        }
        Map<String, Object> beforeSnapshot = MedicationLogSnapshot.of(record, support.userName(record.getExecutorId()));
        LocalDate lastDate = firstDate.plusDays(courseDays - 1L);
        LocalDate newEnd = lastDate.plusDays(usage.withdrawalDays());
        LocalDate before = currentRelease(record.getBatchId());
        LocalDate after = releaseExcluding(record.getBatchId(), record.getId(), newEnd);
        if (before != null && after != null && after.isBefore(before)) {
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED,
                    support.staffView()
                            ? "这项修改会使可出栏日期提前，需要由所有者或场长提交修改申请。你可以通知场长处理。"
                            : "这项修改会使可出栏日期提前，需要提交修改申请")
                    .with("reason", "ADVANCE")
                    .with("release_date_before", before.toString())
                    .with("release_date_after", after.toString());
        }
        DrugLotOption lot = support.findLot(support.lotsFor(drug.getId(), HealthCodes.PURPOSE_MEDICATE),
                request.getDrugLotId() == null ? record.getDrugLotId() : request.getDrugLotId());
        support.assertLotUsable(lot, usedQty, drug.getName(), HealthCodes.unitText(drug.getUnit()));
        drugStockPort.restore(DrugStockCommand.BIZ_MEDICATION_RECORD, record.getId());
        record.setCatalogId(drug.getCatalogId());
        record.setDrugId(drug.getId());
        record.setDrugName(drug.getName());
        record.setDrugLotId(lot.lotId());
        record.setLotNo(lot.lotNo());
        record.setLotExpireDate(lot.expireDate());
        record.setDose(dose);
        record.setDoseUnit(usage.doseUnit());
        record.setRecommendedDose(usage.recommendedDose());
        record.setRouteCode(usage.routeCode());
        record.setCourseDays(courseDays);
        record.setFirstDate(firstDate);
        record.setLastDate(lastDate);
        record.setWithdrawalDays(usage.withdrawalDays());
        record.setWithdrawalEndDate(newEnd);
        record.setOverDose(usage.recommendedDose() != null && dose.compareTo(usage.recommendedDose()) > 0);
        record.setOverDoseNote(request.getOverDoseNote() == null ? record.getOverDoseNote() : request.getOverDoseNote());
        record.setCategoryMismatch(!usage.applicable());
        record.setUsageNote(request.getUsageNote() == null ? record.getUsageNote() : request.getUsageNote());
        record.setReason(request.getReason() == null ? record.getReason() : request.getReason());
        record.setUsedQty(usedQty);
        record.setUsedUnit(drug.getUnit());
        record.setReportId(request.getReportId() == null ? record.getReportId() : request.getReportId());
        if (request.getExecutorId() != null) {
            record.setExecutorId(request.getExecutorId());
        }
        record.setVersion(request.getVersion());
        int rows = recordMapper.updateById(record);
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        StockConsumeResult consume = drugStockPort.deduct(new DrugStockCommand(
                record.getFarmId(), record.getDrugId(), lot.lotId(), usedQty,
                DrugStockCommand.FLOW_MEDICATION_USE, DrugStockCommand.BIZ_MEDICATION_RECORD, record.getId(),
                record.getBatchId(), record.getPenId(), record.getExecutorId(), null, firstDate, drug.getName()));
        recordMapper.update(null, Wrappers.<MedicationRecord>lambdaUpdate()
                .eq(MedicationRecord::getId, record.getId())
                .set(MedicationRecord::getStockFlowId, consume.flowId())
                .set(MedicationRecord::getUnitPrice, consume.unitPrice())
                .set(MedicationRecord::getCostAmount, consume.amount()));
        recalculate(record.getBatchId());
        Map<String, Object> afterSnapshot = MedicationLogSnapshot.of(record, support.userName(record.getExecutorId()));
        if (!beforeSnapshot.equals(afterSnapshot)) {
            audit.record(record.getFarmId(), "MEDICATION_RECORD", record.getId(), "UPDATE", record.getDrugName(),
                    support.batchNo(record.getBatchId()), beforeSnapshot, afterSnapshot);
        }
        return detail(recordId);
    }

    // ===== API-D25 删除影响 =====

    @Override
    public DeleteImpactVO deleteImpact(Long recordId) {
        MedicationRecord record = requireRecord(recordId);
        checkRecordAccess(record);
        BatchInfo batch = support.requireBatch(record.getBatchId());
        DeleteImpactVO vo = new DeleteImpactVO();
        vo.setRecordTitle(record.getDrugName() + " · " + HealthFormat.rangeText(record.getFirstDate(),
                record.getLastDate()));
        vo.setReleaseDateBefore(currentRelease(record.getBatchId()));
        vo.setReleaseDateAfter(releaseExcluding(record.getBatchId(), record.getId(), null));
        vo.setImpact(impact(record, batch, vo.getReleaseDateBefore(), vo.getReleaseDateAfter()));
        fillNotify(vo);
        return vo;
    }

    // ===== API-D26 删除 =====

    @Override
    @Transactional
    public ToastVO delete(Long recordId, Integer version) {
        support.requireManager("只有所有者和场长可以删除用药记录");
        MedicationRecord record = requireRecord(recordId);
        if (version == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 version");
        }
        BatchInfo batch = support.requireBatch(record.getBatchId());
        LocalDate before = currentRelease(record.getBatchId());
        LocalDate after = releaseExcluding(record.getBatchId(), record.getId(), null);
        String impact = impact(record, batch, before, after);
        if (!HealthCodes.IMPACT_NO_CHANGE_TODAY.equals(impact)) {
            String message = switch (impact) {
                case HealthCodes.IMPACT_ADVANCE -> "删除会使可出栏日期提前（"
                        + HealthFormat.dateText(before) + " → " + HealthFormat.dateText(after)
                        + "），需要提交修改申请";
                case HealthCodes.IMPACT_LOCKED -> "记录已锁定，请提交修改申请";
                default -> "只能删除当天登记的记录，请提交修改申请";
            };
            throw new BizException(ErrorCode.CHANGE_REQUEST_REQUIRED, message)
                    .with("reason", HealthCodes.IMPACT_ADVANCE.equals(impact) ? "ADVANCE" : impact);
        }
        record.setVersion(version);
        int rows = recordMapper.deleteById(record);
        if (rows == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, "内容已更新，请刷新后再修改");
        }
        drugStockPort.restore(DrugStockCommand.BIZ_MEDICATION_RECORD, record.getId());
        recalculate(record.getBatchId());
        audit.record(record.getFarmId(), "MEDICATION_RECORD", record.getId(), "DELETE", record.getDrugName(),
                support.batchNo(record.getBatchId()), Map.of("deleted", false), Map.of("deleted", true));
        return ToastVO.of("已删除用药记录");
    }

    // ===== API-D27 通知场长 =====

    @Override
    public NotifyResultVO notifyManager(Long recordId, NotifyManagerRequest request) {
        MedicationRecord record = requireRecord(recordId);
        if (!support.staffView()) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只有员工可以通知场长");
        }
        if (UserContext.userId() == null || !UserContext.userId().equals(record.getCreatedBy())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能通知本人登记的记录");
        }
        List<FarmMemberRow> managers = notifyReceivers();
        if (managers.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "本场没有可通知的场长");
        }
        String key = "rate:D27:" + UserContext.userId() + ":" + recordId;
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(10));
        NotifyResultVO vo = new NotifyResultVO();
        for (FarmMemberRow row : managers) {
            vo.getNotifiedNames().add(row.getName());
        }
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(ErrorCode.TOO_FREQUENT,
                    "已通知" + String.join("、", vo.getNotifiedNames()) + "，请勿重复发送")
                    .with("cooldown_seconds", 600);
        }
        String action = request == null || request.getAction() == null ? "UPDATE" : request.getAction();
        Set<Long> receivers = new LinkedHashSet<>();
        for (FarmMemberRow row : managers) {
            receivers.add(row.getUserId());
        }
        messageService.send(MessageCommand.builder()
                .msgType(MsgType.STAFF_NOTIFY)
                .farmId(support.farmId())
                .receiverUserIds(receivers)
                .title(support.userName(UserContext.userId()) + "请你处理用药记录")
                .summary(support.batchNo(record.getBatchId()) + " · " + record.getDrugName() + " · "
                        + HealthFormat.rangeText(record.getFirstDate(), record.getLastDate()))
                .bizType(BizType.MEDICATION_RECORD)
                .bizId(recordId)
                .route("D05")
                .extra("action", action)
                .build());
        return vo;
    }

    // ===== 休药重算 =====

    /** 重算并在没有行时插入；返回该批次的当前可出栏日期 */
    public LocalDate recalculate(Long batchId) {
        BatchWithdrawal row = withdrawalMapper.selectForUpdate(batchId);
        LocalDate today = BeijingTime.today();
        List<MedicationRecord> records = effective(batchId);
        LocalDate release = null;
        MedicationRecord decisive = null;
        for (MedicationRecord record : records) {
            if (record.getWithdrawalEndDate() == null) {
                continue;
            }
            if (release == null || record.getWithdrawalEndDate().isAfter(release)) {
                release = record.getWithdrawalEndDate();
                decisive = record;
            }
        }
        if (row == null) {
            row = new BatchWithdrawal();
            row.setFarmId(support.farmId());
            row.setBatchId(batchId);
            row.setReleaseDate(release);
            row.setDecisiveRecordId(decisive == null ? null : decisive.getId());
            row.setDecisiveDrugName(decisive == null ? null : decisive.getDrugName());
            row.setLastMedDate(decisive == null ? null : decisive.getLastDate());
            row.setWithdrawalDays(decisive == null ? null : decisive.getWithdrawalDays());
            row.setCycleNo(release != null && today.isBefore(release) ? 1 : 0);
            row.setRecalculatedAt(BeijingTime.now());
            withdrawalMapper.insert(row);
            return release;
        }
        Integer oldCycle = row.getCycleNo() == null ? 0 : row.getCycleNo();
        boolean entering = release != null && today.isBefore(release)
                && (row.getReleaseDate() == null || !today.isBefore(row.getReleaseDate()));
        row.setReleaseDate(release);
        row.setDecisiveRecordId(decisive == null ? null : decisive.getId());
        row.setDecisiveDrugName(decisive == null ? null : decisive.getDrugName());
        row.setLastMedDate(decisive == null ? null : decisive.getLastDate());
        row.setWithdrawalDays(decisive == null ? null : decisive.getWithdrawalDays());
        row.setCycleNo(entering ? oldCycle + 1 : oldCycle);
        row.setRecalculatedAt(BeijingTime.now());
        withdrawalMapper.updateById(row);
        return release;
    }

    private LocalDate currentRelease(Long batchId) {
        BatchWithdrawal row = withdrawalMapper.selectByBatchId(batchId);
        return row == null ? null : row.getReleaseDate();
    }

    /** 排除某条记录后的可出栏日期；newEnd 非空时为该记录的新结束日期（修改预判用） */
    private LocalDate releaseExcluding(Long batchId, Long excludeId, LocalDate newEnd) {
        LocalDate release = null;
        for (MedicationRecord record : effective(batchId)) {
            if (record.getId().equals(excludeId)) {
                if (newEnd != null && (release == null || newEnd.isAfter(release))) {
                    release = newEnd;
                }
                continue;
            }
            if (record.getWithdrawalEndDate() != null
                    && (release == null || record.getWithdrawalEndDate().isAfter(release))) {
                release = record.getWithdrawalEndDate();
            }
        }
        return release;
    }

    private String impact(MedicationRecord record, BatchInfo batch, LocalDate before, LocalDate after) {
        if (!batch.active()) {
            return HealthCodes.IMPACT_LOCKED;
        }
        if (before != null && after != null && after.isBefore(before)) {
            return HealthCodes.IMPACT_ADVANCE;
        }
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        return sameDay ? HealthCodes.IMPACT_NO_CHANGE_TODAY : HealthCodes.IMPACT_NO_CHANGE_EARLIER;
    }

    private void fillNotify(DeleteImpactVO vo) {
        for (FarmMemberRow row : support.members()) {
            if ("MANAGER".equals(row.getRole())) {
                vo.setNotifyName(row.getName());
                vo.setNotifyRole("MANAGER");
                return;
            }
        }
        for (FarmMemberRow row : support.members()) {
            if ("OWNER".equals(row.getRole())) {
                vo.setNotifyName(row.getName());
                vo.setNotifyRole("OWNER");
                return;
            }
        }
    }

    private List<FarmMemberRow> notifyReceivers() {
        List<FarmMemberRow> managers = new ArrayList<>();
        for (FarmMemberRow row : support.members()) {
            if ("MANAGER".equals(row.getRole())) {
                managers.add(row);
            }
        }
        if (managers.isEmpty()) {
            for (FarmMemberRow row : support.members()) {
                if ("OWNER".equals(row.getRole())) {
                    managers.add(row);
                }
            }
        }
        return managers;
    }

    // ===== 组装 =====

    private MedicationRecordVO toVO(MedicationRecord record, BatchInfo batch, boolean detail) {
        Map<Long, String> names = support.memberNames();
        MedicationRecordVO vo = new MedicationRecordVO();
        vo.setId(record.getId());
        vo.setGroupNo(record.getGroupNo());
        vo.setVersion(record.getVersion());
        vo.setTargetType(record.getTargetType());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batch == null ? null : batch.batchNo());
        vo.setPenId(record.getPenId());
        vo.setPenName(support.penName(record.getPenId()));
        vo.setDrugId(record.getDrugId());
        vo.setDrugName(record.getDrugName());
        vo.setRouteCode(record.getRouteCode());
        vo.setRouteName(HealthCodes.routeText(record.getRouteCode()));
        vo.setLotNo(record.getLotNo());
        vo.setLotExpireDate(record.getLotExpireDate());
        vo.setDose(record.getDose());
        vo.setDoseUnit(HealthCodes.drugDoseUnitText(record.getDoseUnit()));
        vo.setRecommendedDose(record.getRecommendedDose());
        vo.setOverDose(Boolean.TRUE.equals(record.getOverDose()));
        vo.setCourseDays(record.getCourseDays());
        vo.setFirstDate(record.getFirstDate());
        vo.setLastDate(record.getLastDate());
        vo.setWithdrawalDays(record.getWithdrawalDays());
        vo.setWithdrawalEndDate(record.getWithdrawalEndDate());
        vo.setEstimatedQty(HealthFormat.plain(record.getEstimatedQty()));
        vo.setUsedQty(HealthFormat.plain(record.getUsedQty()));
        vo.setUsedUnit(HealthCodes.unitText(record.getUsedUnit()));
        vo.setReason(record.getReason());
        vo.setCreatorName(names.getOrDefault(record.getCreatedBy(), support.userName(record.getCreatedBy())));
        vo.setExecutorName(names.getOrDefault(record.getExecutorId(), support.userName(record.getExecutorId())));
        vo.setIsMine(UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy()));
        boolean locked = batch != null && !batch.active();
        vo.setLocked(locked);
        vo.setUnitPrice(record.getUnitPrice());
        vo.setCostAmount(record.getCostAmount());
        vo.setDrugLotId(record.getDrugLotId());
        vo.setExecutorId(record.getExecutorId());
        ChangeRequestStateVO state = changeState(record, locked);
        vo.setChangeState(state);
        vo.setActions(actions(record, locked));
        if (detail) {
            vo.setOverDoseNote(record.getOverDoseNote());
            vo.setUsageNote(record.getUsageNote());
            vo.setCategoryMismatch(Boolean.TRUE.equals(record.getCategoryMismatch()));
            vo.setReportId(record.getReportId());
            vo.setReleaseDateNow(currentRelease(record.getBatchId()));
        }
        return vo;
    }

    private ChangeRequestStateVO changeState(MedicationRecord record, boolean locked) {
        boolean sameDay = record.getCreatedAt() != null
                && record.getCreatedAt().toLocalDate().equals(BeijingTime.today());
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        boolean canEdit = !locked && sameDay && (support.managerView() || mine);
        ChangeRequestStateVO state = new ChangeRequestStateVO();
        state.setCanEdit(canEdit);
        state.setEditDeadline(canEdit && record.getCreatedAt() != null
                ? BeijingTime.editDeadline(record.getCreatedAt().toLocalDate(), 0).minusSeconds(1) : null);
        return state;
    }

    private List<String> actions(MedicationRecord record, boolean locked) {
        List<String> actions = new ArrayList<>();
        boolean mine = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        if (support.managerView()) {
            if (Boolean.TRUE.equals(changeState(record, locked).getCanEdit())) {
                actions.add(HealthCodes.ACTION_EDIT);
            } else {
                actions.add(HealthCodes.ACTION_CHANGE_REQUEST);
            }
            actions.add(HealthCodes.ACTION_DELETE);
            actions.add(HealthCodes.ACTION_VIEW_LOG);
        } else if (mine) {
            if (Boolean.TRUE.equals(changeState(record, locked).getCanEdit())) {
                actions.add(HealthCodes.ACTION_EDIT);
            } else {
                actions.add(HealthCodes.ACTION_NOTIFY);
            }
            actions.add(HealthCodes.ACTION_VIEW_LOG);
        }
        return actions;
    }

    // ===== 基础数据与用量 =====

    private record Usage(String routeCode, BigDecimal recommendedDose, String doseUnit, Integer courseDays,
                         Integer withdrawalDays, boolean applicable) {
    }

    private Usage usage(Long catalogId, String species) {
        DrugCatalogUsage usage = drugCatalogQueryService.usage(catalogId, species);
        int maxWithdrawal = 0;
        for (DrugCatalogUsage item : drugCatalogQueryService.usages(catalogId)) {
            if (item.getWithdrawalDays() != null && item.getWithdrawalDays() > maxWithdrawal) {
                maxWithdrawal = item.getWithdrawalDays();
            }
        }
        if (usage == null) {
            return new Usage(null, null, null, null, maxWithdrawal, false);
        }
        int withdrawal = usage.getWithdrawalDays() == null ? maxWithdrawal : usage.getWithdrawalDays();
        return new Usage(usage.getAdminRoute(), usage.getRecommendedDose(), usage.getDoseUnit(),
                usage.getCourseDays(), withdrawal, Boolean.TRUE.equals(usage.getApplicable()));
    }

    /** 预计用量：按头、按体重才有值；按水或按料计量无法由头数推出，返回 null */
    private BigDecimal estimate(Usage usage, BigDecimal dose, Integer headCount, Integer courseDays, String unit) {
        if (dose == null || usage.doseUnit() == null || headCount == null || courseDays == null) {
            return null;
        }
        BigDecimal perHead = switch (usage.doseUnit()) {
            case "ML_PER_HEAD", "G_PER_HEAD" -> dose;
            default -> null;
        };
        if (perHead == null) {
            return null;
        }
        BigDecimal total = perHead.multiply(BigDecimal.valueOf(headCount))
                .multiply(BigDecimal.valueOf(courseDays));
        if ("L".equals(unit) && "ML_PER_HEAD".equals(usage.doseUnit())) {
            total = total.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    private String resolveSpecies(Long batchId, Long penId) {
        if (batchId != null) {
            return support.species(support.requireBatch(batchId).categoryId());
        }
        for (BatchPenStockRow row : support.activeBatchesOfPen(penId)) {
            return support.species(support.requireBatch(row.getBatchId()).categoryId());
        }
        return "PIG";
    }

    private List<BatchInfo> activeBatches() {
        return batchQueryService.listActive(support.farmId());
    }

    private Map<Long, FarmDrugRow> drugRows() {
        Map<Long, FarmDrugRow> map = new LinkedHashMap<>();
        for (FarmDrugRow row : referenceMapper.selectDrugs(support.farmId())) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private Map<Long, BigDecimal> stockByDrug() {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (DrugStockRow row : referenceMapper.selectAvailableStock(support.farmId(), BeijingTime.today())) {
            map.put(row.getDrugId(), row.getStockQty() == null ? BigDecimal.ZERO : row.getStockQty());
        }
        return map;
    }

    private List<MedicationRecord> effective(Long batchId) {
        return recordMapper.selectEffective(support.farmId(), batchId);
    }

    private MedicationRecord requireRecord(Long recordId) {
        MedicationRecord record = recordId == null ? null : recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return record;
    }

    private void checkRecordAccess(MedicationRecord record) {
        if (support.staffView() && !support.member().canAccessPen(record.getPenId())) {
            throw new BizException(ErrorCode.PEN_FORBIDDEN, "你没有这个栏舍的查看权限");
        }
        if (support.staffView() && !support.managerView()
                && UserContext.userId() != null && !UserContext.userId().equals(record.getCreatedBy())
                && !isSameStaffRecord(record)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    /** 员工只能看本人登记或负责栏舍的记录；负责栏舍的他人记录可看（列表口径） */
    private boolean isSameStaffRecord(MedicationRecord record) {
        return support.member().canAccessPen(record.getPenId());
    }

    private Long executor(Long requested, Long penId) {
        if (support.staffView()) {
            return UserContext.requireUserId();
        }
        if (requested != null) {
            for (FarmMemberRow member : support.members()) {
                if (requested.equals(member.getUserId())) {
                    return requested;
                }
            }
        }
        Long defaultExecutor = support.defaultExecutorId(penId);
        return defaultExecutor == null ? UserContext.requireUserId() : defaultExecutor;
    }

    private String clientRequestId() {
        String key = RequestContext.idempotencyKey();
        return key == null || key.isBlank() ? "AUTO-" + UUID.randomUUID() : key;
    }

    private String nextGroupNo() {
        return "MG" + BeijingTime.now().format(GROUP_TIME)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private MedicationCreateResultVO existingResult(String clientRequestId, List<BatchPenStockRow> batchPens) {
        if (clientRequestId == null || clientRequestId.startsWith("AUTO-")) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        String groupNo = null;
        List<MedicationCreateResultVO.PerBatch> perBatch = new ArrayList<>();
        LocalDate latest = null;
        for (BatchPenStockRow row : batchPens) {
            MedicationRecord existing = recordMapper.selectByClientRequestId(support.farmId(), clientRequestId,
                    row.getBatchId());
            if (existing == null) {
                return null;
            }
            ids.add(existing.getId());
            groupNo = existing.getGroupNo();
            MedicationCreateResultVO.PerBatch item = new MedicationCreateResultVO.PerBatch();
            item.setBatchNo(support.batchNo(row.getBatchId()));
            item.setReleaseDate(currentRelease(row.getBatchId()));
            perBatch.add(item);
            if (item.getReleaseDate() != null && (latest == null || item.getReleaseDate().isAfter(latest))) {
                latest = item.getReleaseDate();
            }
        }
        MedicationCreateResultVO result = new MedicationCreateResultVO();
        result.setRecordIds(ids);
        result.setGroupNo(groupNo);
        result.setPerBatch(perBatch);
        if (latest != null) {
            result.setToast("已登记用药，可出栏日期为 " + HealthFormat.dateWithWeek(latest));
        }
        return result;
    }

    private boolean contains(String text, String keyword) {
        return text != null && text.toLowerCase().contains(keyword.toLowerCase());
    }
}
