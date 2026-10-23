package com.zhimu.server.biz.feeding.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.base.api.StdQueryService;
import com.zhimu.server.admin.base.vo.BreedStandardsVO;
import com.zhimu.server.biz.feeding.entity.BatchGrowth;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecordItem;
import com.zhimu.server.biz.feeding.mapper.BatchGrowthMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordItemMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.feeding.request.WeighCycleRequest;
import com.zhimu.server.biz.feeding.request.WeighItemRequest;
import com.zhimu.server.biz.feeding.request.WeighRecordCreateRequest;
import com.zhimu.server.biz.feeding.request.WeighRecordUpdateRequest;
import com.zhimu.server.biz.feeding.support.FeedBatchRow;
import com.zhimu.server.biz.feeding.support.FeedCalc;
import com.zhimu.server.biz.feeding.support.FeedErrors;
import com.zhimu.server.biz.feeding.support.FeedLookup;
import com.zhimu.server.biz.feeding.support.GrowthCalc;
import com.zhimu.server.biz.feeding.vo.WeighVO;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.PenInfo;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** API-G18–G26 称重与生长曲线（技术准备文档 2.5.6、2.6.4、2.7.6、2.7.7）。 */
@Service
@RequiredArgsConstructor
public class WeighService {

    private static final BigDecimal SAMPLE_RATIO = new BigDecimal("0.15");

    private final WeighRecordMapper recordMapper;
    private final WeighRecordItemMapper itemMapper;
    private final BatchGrowthMapper growthMapper;
    private final FeedLookup lookup;
    private final StdQueryService stdQueryService;

    /** G18 称重批次选择 */
    public List<WeighVO.BatchOption> batchOptions(boolean includeEnded) {
        FarmContext.FarmMember context = lookup.context();
        List<WeighVO.BatchOption> options = new ArrayList<>();
        for (FeedBatchRow row : lookup.farmBatches(includeEnded)) {
            if (!includeEnded && !"ACTIVE".equals(row.getStatus())) {
                continue;
            }
            List<Long> penIds = lookup.batchPenIds(row.getBatchId());
            if (!lookup.staffCanSeePens(penIds)) {
                continue;
            }
            BatchGrowth growth = growth(synthetic(row));
            WeighVO.BatchOption vo = new WeighVO.BatchOption();
            vo.setBatchId(row.getBatchId());
            vo.setBatchNo(row.getBatchNo());
            vo.setPenNames(lookup.penNames(penIds));
            vo.setBreedName(lookup.breedName(row.getBreedId()));
            vo.setDayAge(FeedCalc.dayAge(synthetic(row), BeijingTime.today()));
            vo.setStatus(row.getStatus());
            vo.setLastAvgWeight(growth.getLastAvgWeight());
            vo.setGrowthStatus(growth.getGrowthStatus());
            vo.setGrowthStatusText(FeedCalc.growthStatusText(growth.getGrowthStatus()));
            options.add(vo);
        }
        options.sort(Comparator.comparing(WeighVO.BatchOption::getStatus,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WeighVO.BatchOption::getBatchNo,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return options;
    }

    /** G19 称重表单上下文 */
    public WeighVO.Form form(Long batchId, String dateText) {
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        lookup.checkBatchScope(batch);
        LocalDate date = parseDate(dateText);
        if (date == null) {
            date = BeijingTime.today();
        }
        validateDate(batch, date);
        int dayAge = FeedCalc.dayAge(batch, date);
        int stock = stockCount(batch, date);
        WeighRecord sameDay = recordByDate(batchId, date);
        WeighRecord last = lastBefore(batchId, date);

        WeighVO.Form vo = new WeighVO.Form();
        vo.setBatch(toBatch(batch));
        vo.setWeighDate(BeijingTime.format(date));
        vo.setDayAge(dayAge);
        vo.setStockCount(stock);
        vo.setFirstWeigh(last == null);
        if (last != null) {
            WeighVO.LastWeigh lastVo = new WeighVO.LastWeigh();
            lastVo.setDate(BeijingTime.format(last.getWeighDate()));
            lastVo.setAvgWeight(last.getAvgWeight());
            vo.setLastWeigh(lastVo);
        }
        vo.setExistingRecordId(sameDay == null ? null : sameDay.getId());
        vo.setCanRegister(batch.active() && sameDay == null);
        if (!batch.active()) {
            vo.setBlockMessage("批次已结束，不能再登记称重");
        } else if (sameDay != null) {
            vo.setBlockMessage("当日已登记称重，请修改原记录");
        }
        Standards standards = standards(batch.breedId());
        vo.setStdAvailable(!standards.points().isEmpty());
        vo.setStdPoints(standards.points());
        vo.setStdWeightAtDate(GrowthCalc.interpolate(standards.points(), dayAge));
        vo.setAllowedDeviation(standards.tolerance());
        BatchGrowth growth = growth(batch);
        vo.setCurrentGrowthStatus(growth.getGrowthStatus());
        vo.setSampleRatio(SAMPLE_RATIO);
        vo.setSuggestedSample(GrowthCalc.suggestedSample(stock));
        vo.setWeighCycle(cycleVo(batch, growth));
        vo.setCanEditCycle(context().isManagerView() && batch.active());
        return vo;
    }

    /** G20 提交称重 */
    @Transactional
    public WeighVO.Record create(Long batchId, WeighRecordCreateRequest request, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw FeedErrors.field("Idempotency-Key", "缺少幂等键");
        }
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        lookup.checkBatchScope(batch);
        if (!batch.active()) {
            throw FeedErrors.stateChanged("批次 " + batch.batchNo() + " 已结束，不能再登记称重。");
        }
        WeighRecord existing = recordMapper.selectOne(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getCreatedBy, UserContext.requireUserId())
                .eq(WeighRecord::getRequestId, requestId.trim())
                .last("LIMIT 1"));
        if (existing != null) {
            return detail(existing.getId(), null);
        }
        if (request == null) {
            throw FeedErrors.field("mode", "请选择录入方式");
        }
        LocalDate date = parseDate(request.getWeighDate());
        if (date == null) {
            throw FeedErrors.field("weigh_date", "请填写称重日期");
        }
        validateDate(batch, date);
        if (recordByDate(batchId, date) != null) {
            throw FeedErrors.stateChanged(monthDay(date) + "已登记称重，请修改原记录");
        }
        int stock = stockCount(batch, date);
        Measurement measurement = measure(request.getMode(), request.getSampleCount(), request.getTotalWeight(),
                request.getItems(), stock);
        int dayAge = FeedCalc.dayAge(batch, date);
        Standards standards = standards(batch.breedId());
        BigDecimal stdWeight = GrowthCalc.interpolate(standards.points(), dayAge);
        BigDecimal deviation = GrowthCalc.deviation(measurement.avgWeight(), stdWeight);
        String growthResult = GrowthCalc.growthResult(deviation, standards.tolerance());
        Gain gain = gain(batch, date, measurement.avgWeight());

        WeighRecord record = new WeighRecord();
        record.setFarmId(lookup.farmId());
        record.setBatchId(batchId);
        record.setWeighDate(date);
        record.setDayAge(dayAge);
        record.setStockCount(stock);
        record.setMode(measurement.mode());
        record.setSampleCount(measurement.sampleCount());
        record.setTotalWeight(measurement.totalWeight());
        record.setAvgWeight(measurement.avgWeight());
        record.setSuggestedSample(GrowthCalc.suggestedSample(stock));
        record.setSampleLow(measurement.sampleCount() < GrowthCalc.suggestedSample(stock));
        record.setStdVersionId(standards.versionId());
        record.setStdWeight(stdWeight);
        record.setAllowedDeviation(standards.tolerance());
        record.setDeviationRatio(deviation);
        record.setGrowthResult(growthResult);
        record.setGainBaseType(gain.type());
        record.setGainBaseDate(gain.baseDate());
        record.setGainBaseWeight(gain.baseWeight());
        record.setDailyGain(gain.dailyGain());
        record.setRequestId(requestId.trim());
        record.setVersion(0);
        record.setUpdatedBy(UserContext.requireUserId());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw FeedErrors.stateChanged(monthDay(date) + "已登记称重，请修改原记录");
        }
        saveItems(record, measurement.items());
        recalcNext(record);
        String growthChange = updateGrowth(batch, record, true);
        WeighVO.Record vo = detail(record.getId(), growthChange);
        vo.setTodoCompleted(null);
        return vo;
    }

    /** G21 称重记录详情 */
    public WeighVO.Record detail(Long id) {
        return detail(id, null);
    }

    private WeighVO.Record detail(Long id, String growthChange) {
        WeighRecord record = requireRecord(id);
        BatchInfo batch = lookup.requireOwnedBatch(record.getBatchId());
        lookup.checkBatchScope(batch);
        FarmContext.FarmMember context = lookup.context();
        WeighVO.Record vo = new WeighVO.Record();
        vo.setId(record.getId());
        vo.setBatchId(record.getBatchId());
        vo.setBatchNo(batch.batchNo());
        vo.setWeighDate(BeijingTime.format(record.getWeighDate()));
        vo.setDayAge(record.getDayAge());
        vo.setStockCount(record.getStockCount());
        vo.setMode(record.getMode());
        vo.setSampleCount(record.getSampleCount());
        vo.setTotalWeight(record.getTotalWeight());
        vo.setAvgWeight(record.getAvgWeight());
        vo.setSuggestedSample(record.getSuggestedSample());
        vo.setSampleLow(record.getSampleLow());
        vo.setStdWeight(record.getStdWeight());
        vo.setAllowedDeviation(record.getAllowedDeviation());
        vo.setDeviationRatio(record.getDeviationRatio());
        vo.setOutOfRange("SLOW".equals(record.getGrowthResult()) || "FAST".equals(record.getGrowthResult()));
        vo.setGrowthResult(record.getGrowthResult());
        vo.setGrowthResultText(FeedCalc.growthResultText(record.getGrowthResult()));
        vo.setGainBaseType(record.getGainBaseType());
        vo.setGainBaseDate(BeijingTime.format(record.getGainBaseDate()));
        vo.setGainBaseWeight(record.getGainBaseWeight());
        vo.setDailyGain(record.getDailyGain());
        UserBriefVO recorder = lookup.userBrief(record.getCreatedBy());
        vo.setRecorder(recorder);
        vo.setCreatedAt(BeijingTime.format(record.getCreatedAt()));
        boolean today = BeijingTime.today().equals(record.getWeighDate());
        boolean manager = context.isManagerView();
        boolean own = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        boolean canEdit = batch.active() && (manager || (context.isStaff() && own && today));
        vo.setCanRequestChange(batch.active() && !canEdit);
        vo.setChangeState(ChangeRequestStateVO.builder()
                .canEdit(canEdit)
                .editDeadline(record.getWeighDate() == null ? null : record.getWeighDate().plusDays(1).atStartOfDay())
                .pendingRequestId(null)
                .build());
        vo.setChangeFields(List.of("sample_count", "total_weight"));
        vo.setRecalcTags(List.of("生长曲线", "日增重", "出栏建议"));
        vo.setGrowthChange(growthChange);
        vo.setVersion(record.getVersion() == null ? 0 : record.getVersion());
        if ("INDIVIDUAL".equals(record.getMode())) {
            List<WeighRecordItem> items = itemMapper.selectList(Wrappers.<WeighRecordItem>lambdaQuery()
                    .eq(WeighRecordItem::getWeighRecordId, record.getId())
                    .orderByAsc(WeighRecordItem::getSeqNo));
            for (WeighRecordItem item : items) {
                WeighVO.Item voItem = new WeighVO.Item();
                voItem.setSeqNo(item.getSeqNo());
                voItem.setWeight(item.getWeight());
                vo.getItems().add(voItem);
            }
        }
        BatchGrowth growth = growth(batch);
        if (record.getId().equals(growth.getLastWeighId())) {
            vo.setNextWeighDate(BeijingTime.format(growth.getNextWeighDate()));
        }
        return vo;
    }

    /** G22 修改称重记录 */
    @Transactional
    public WeighVO.Record update(Long id, WeighRecordUpdateRequest request) {
        WeighRecord record = requireRecord(id);
        BatchInfo batch = lookup.requireOwnedBatch(record.getBatchId());
        lookup.checkBatchScope(batch);
        FarmContext.FarmMember context = lookup.context();
        boolean manager = context.isManagerView();
        boolean today = BeijingTime.today().equals(record.getWeighDate());
        boolean own = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
        if (!manager && !(context.isStaff() && own && today)) {
            throw FeedErrors.roleForbidden(manager ? "批次已结束，修改称重记录需要提交修改申请"
                    : "只能修改你本人登记的称重记录");
        }
        if (!batch.active()) {
            throw FeedErrors.changeRequired("批次已结束，修改称重记录需要提交修改申请");
        }
        if (context.isStaff() && !today) {
            throw FeedErrors.changeRequired("已超过可直接修改的时间，请提交修改申请");
        }
        if (request == null || request.getVersion() == null) {
            throw FeedErrors.field("version", "缺少版本号");
        }
        if (!request.getVersion().equals(record.getVersion())) {
            throw FeedErrors.versionConflict();
        }
        Measurement measurement = measure(request.getMode(), request.getSampleCount(), request.getTotalWeight(),
                request.getItems(), record.getStockCount());
        BigDecimal stdWeight = record.getStdWeight();
        BigDecimal deviation = GrowthCalc.deviation(measurement.avgWeight(), stdWeight);
        String growthResult = GrowthCalc.growthResult(deviation, record.getAllowedDeviation());
        Gain gain = gain(batch, record.getWeighDate(), measurement.avgWeight());
        record.setMode(measurement.mode());
        record.setSampleCount(measurement.sampleCount());
        record.setTotalWeight(measurement.totalWeight());
        record.setAvgWeight(measurement.avgWeight());
        record.setSampleLow(measurement.sampleCount() < record.getSuggestedSample());
        record.setDeviationRatio(deviation);
        record.setGrowthResult(growthResult);
        record.setGainBaseType(gain.type());
        record.setGainBaseDate(gain.baseDate());
        record.setGainBaseWeight(gain.baseWeight());
        record.setDailyGain(gain.dailyGain());
        record.setUpdatedBy(UserContext.requireUserId());
        if (recordMapper.updateById(record) == 0) {
            throw FeedErrors.versionConflict();
        }
        itemMapper.delete(Wrappers.<WeighRecordItem>lambdaQuery()
                .eq(WeighRecordItem::getWeighRecordId, record.getId()));
        saveItems(record, measurement.items());
        recalcNext(record);
        String growthChange = updateGrowth(batch, record, true);
        return detail(record.getId(), growthChange);
    }

    /** G23 修改 / 恢复称重周期 */
    @Transactional
    public WeighVO.Cycle updateCycle(Long batchId, WeighCycleRequest request) {
        lookup.requireManager("你没有修改称重周期的权限");
        BatchInfo batch = lookup.requireActiveBatch(batchId);
        if (request == null || (request.getPerMonth() == null && !Boolean.TRUE.equals(request.getReset()))) {
            throw FeedErrors.field("per_month", "请填写称重周期");
        }
        if (request.getPerMonth() != null && Boolean.TRUE.equals(request.getReset())) {
            throw FeedErrors.field("reset", "修改周期与恢复建议值只能选一个");
        }
        if (request.getPerMonth() != null && (request.getPerMonth() < 1 || request.getPerMonth() > 8)) {
            throw FeedErrors.field("per_month", "称重周期要在 1 到 8 次 / 月之间");
        }
        BatchGrowth growth = growth(batch);
        if (request.getVersion() != null && !request.getVersion().equals(growth.getVersion())) {
            throw FeedErrors.versionConflict();
        }
        int suggested = suggestedPerMonth(batch.breedId());
        boolean reset = Boolean.TRUE.equals(request.getReset());
        int perMonth = reset ? suggested : request.getPerMonth();
        growth.setWeighCyclePerMonth(perMonth);
        growth.setCycleCustomized(!reset);
        growth.setCycleUpdatedBy(UserContext.requireUserId());
        growth.setCycleUpdatedAt(BeijingTime.now());
        growth.setNextWeighDate(recalcNextWeighDate(batch, growth));
        saveGrowth(batch, growth);
        return cycleVo(batch, growth);
    }

    /** G24 生长曲线 */
    public WeighVO.Curve curve(Long batchId, String compareBatchIds) {
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        lookup.checkBatchScope(batch);
        List<WeighRecord> records = recordsOf(batchId);
        Standards standards = standards(batch.breedId());
        BatchGrowth growth = growth(batch);
        WeighVO.Curve vo = new WeighVO.Curve();
        vo.setBatch(toBatch(batch));
        vo.setGrowthStatus(growth.getGrowthStatus());
        vo.setGrowthStatusText(FeedCalc.growthStatusText(growth.getGrowthStatus()));
        vo.setStdAvailable(!standards.points().isEmpty());
        vo.setStdPoints(standards.points());
        int entryAge = batch.entryAgeDays() == null ? 0 : batch.entryAgeDays();
        vo.setCurrentDayAge(FeedCalc.dayAge(batch, BeijingTime.today()));
        vo.setXMin(entryAge);
        int maxDayAge = vo.getCurrentDayAge();
        if (!records.isEmpty()) {
            maxDayAge = Math.max(maxDayAge, records.get(records.size() - 1).getDayAge());
        }
        vo.setXMax(Math.max(maxDayAge, entryAge + 14));

        if (lookup.entryAvgWeight(batch.batchId()) != null) {
            WeighVO.ActualPoint entry = new WeighVO.ActualPoint();
            entry.setDate(BeijingTime.format(batch.entryDate()));
            entry.setDayAge(entryAge);
            entry.setAvgWeight(lookup.entryAvgWeight(batch.batchId()));
            entry.setStdWeight(GrowthCalc.interpolate(standards.points(), entryAge));
            entry.setIsEntry(true);
            vo.getActualPoints().add(entry);
        }
        for (WeighRecord record : records) {
            WeighVO.ActualPoint point = new WeighVO.ActualPoint();
            point.setDate(BeijingTime.format(record.getWeighDate()));
            point.setDayAge(record.getDayAge());
            point.setAvgWeight(record.getAvgWeight());
            point.setStdWeight(record.getStdWeight());
            point.setIsEntry(false);
            vo.getActualPoints().add(point);
        }
        if (!records.isEmpty()) {
            WeighRecord latest = records.get(records.size() - 1);
            WeighVO.Metrics metrics = new WeighVO.Metrics();
            metrics.setAvgWeight(latest.getAvgWeight());
            metrics.setDayAge(latest.getDayAge());
            metrics.setWeighDate(BeijingTime.format(latest.getWeighDate()));
            metrics.setDeviationRatio(latest.getDeviationRatio());
            metrics.setStdWeight(latest.getStdWeight());
            metrics.setAllowedDeviation(latest.getAllowedDeviation());
            metrics.setDailyGain(latest.getDailyGain());
            WeighRecord prev = records.size() > 1 ? records.get(records.size() - 2) : null;
            if (prev != null) {
                metrics.setPrevDailyGain(prev.getDailyGain());
                metrics.setPrevGainBaseType(prev.getGainBaseType());
            } else {
                metrics.setPrevGainBaseType(latest.getGainBaseType());
                if ("INTAKE".equals(latest.getGainBaseType())) {
                    metrics.setPrevDailyGain(latest.getDailyGain());
                }
            }
            if (lookup.entryAvgWeight(batch.batchId()) != null) {
                metrics.setCumulativeGain(FeedCalc.round2(latest.getAvgWeight()
                        .subtract(lookup.entryAvgWeight(batch.batchId()))));
            }
            vo.setMetrics(metrics);
        }
        vo.setStdUpdatedNotice(growth.getStdRecalcAt() != null
                && growth.getStdRecalcAt().isAfter(BeijingTime.now().minusDays(7)));
        comp(batch, compareBatchIds, vo);
        return vo;
    }

    /** G25 称重记录列表 */
    public PageVO<WeighVO.RecordItem> records(Long batchId, int page, int size) {
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        lookup.checkBatchScope(batch);
        FarmContext.FarmMember context = lookup.context();
        Page<WeighRecord> paged = recordMapper.selectPage(new Page<>(page, size),
                Wrappers.<WeighRecord>lambdaQuery()
                        .eq(WeighRecord::getBatchId, batchId)
                        .orderByDesc(WeighRecord::getWeighDate));
        Set<Long> userIds = paged.getRecords().stream().map(WeighRecord::getCreatedBy).collect(Collectors.toSet());
        Map<Long, UserBriefVO> members = lookup.memberBriefs(userIds);
        List<WeighVO.RecordItem> list = new ArrayList<>();
        LocalDate today = BeijingTime.today();
        for (WeighRecord record : paged.getRecords()) {
            WeighVO.RecordItem item = new WeighVO.RecordItem();
            item.setId(record.getId());
            item.setWeighDate(BeijingTime.format(record.getWeighDate()));
            item.setDayAge(record.getDayAge());
            item.setAvgWeight(record.getAvgWeight());
            item.setSampleCount(record.getSampleCount());
            item.setMode(record.getMode());
            UserBriefVO recorder = members.get(record.getCreatedBy());
            item.setRecorderName(recorder == null ? null : recorder.getName());
            item.setStdWeight(record.getStdWeight());
            item.setDeviationRatio(record.getDeviationRatio());
            item.setOutOfRange("SLOW".equals(record.getGrowthResult()) || "FAST".equals(record.getGrowthResult()));
            item.setDailyGain(record.getDailyGain());
            boolean own = UserContext.userId() != null && UserContext.userId().equals(record.getCreatedBy());
            if (batch.active() && (context.isManagerView() || (context.isStaff() && own && today.equals(record.getWeighDate())))) {
                item.setAction("EDIT");
            } else {
                item.setAction(batch.active() ? "REQUEST_CHANGE" : "NONE");
            }
            list.add(item);
        }
        return new PageVO<>(list, paged.getTotal(), page, size);
    }

    /** G26 对比批次选项：同品种、有称重记录的其他批次，按最近一次称重日期倒序 */
    public List<WeighVO.CompareOption> compareOptions(Long batchId) {
        BatchInfo batch = lookup.requireOwnedBatch(batchId);
        lookup.checkBatchScope(batch);
        List<CompareRow> rows = new ArrayList<>();
        for (FeedBatchRow candidate : lookup.farmBatches(true)) {
            if (candidate.getBatchId().equals(batchId) || !candidate.getBreedId().equals(batch.breedId())) {
                continue;
            }
            List<Long> penIds = lookup.batchPenIds(candidate.getBatchId());
            if (!lookup.staffCanSeePens(penIds)) {
                continue;
            }
            WeighRecord latest = latestRecord(candidate.getBatchId());
            if (latest == null) {
                // 没有称重记录的批次没有曲线可对比（App Mock 只列出有称重数据的批次）
                continue;
            }
            WeighVO.CompareOption vo = new WeighVO.CompareOption();
            vo.setBatchId(candidate.getBatchId());
            vo.setBatchNo(candidate.getBatchNo());
            vo.setPenNames(lookup.penNames(penIds));
            vo.setStatus(candidate.getStatus());
            vo.setLatestAvgWeight(latest.getAvgWeight());
            vo.setLatestDayAge(latest.getDayAge());
            rows.add(new CompareRow(vo, latest.getWeighDate()));
        }
        rows.sort(Comparator
                .comparing(CompareRow::lastWeighDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> row.vo().getBatchNo(), Comparator.nullsLast(Comparator.naturalOrder())));
        return rows.stream().map(CompareRow::vo).toList();
    }

    private void comp(BatchInfo batch, String compareBatchIds, WeighVO.Curve vo) {
        if (compareBatchIds == null || compareBatchIds.isBlank()) {
            return;
        }
        String[] parts = compareBatchIds.split(",");
        if (parts.length > 3) {
            throw FeedErrors.field("compare_batch_ids", "最多对比 3 个批次");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            Long id;
            try {
                id = Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                throw FeedErrors.field("compare_batch_ids", "对比批次不正确");
            }
            if (!seen.add(id)) {
                continue;
            }
            BatchInfo other;
            try {
                other = lookup.requireOwnedBatch(id);
            } catch (RuntimeException e) {
                throw FeedErrors.field("compare_batch_ids", "对比批次不存在");
            }
            if (!other.breedId().equals(batch.breedId())) {
                throw FeedErrors.field("compare_batch_ids", "只能对比同品种的批次");
            }
            lookup.checkBatchScope(other);
            WeighVO.CompareSeries series = new WeighVO.CompareSeries();
            series.setBatchId(other.batchId());
            series.setBatchNo(other.batchNo());
            series.setStatus(other.status());
            if (lookup.entryAvgWeight(other.batchId()) != null) {
                WeighVO.ComparePoint point = new WeighVO.ComparePoint();
                point.setDayAge(other.entryAgeDays());
                point.setAvgWeight(lookup.entryAvgWeight(other.batchId()));
                series.getPoints().add(point);
            }
            for (WeighRecord record : recordsOf(other.batchId())) {
                WeighVO.ComparePoint point = new WeighVO.ComparePoint();
                point.setDayAge(record.getDayAge());
                point.setAvgWeight(record.getAvgWeight());
                series.getPoints().add(point);
            }
            vo.getCompareSeries().add(series);
        }
    }

    private void recalcNext(WeighRecord record) {
        WeighRecord next = recordMapper.selectOne(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, record.getBatchId())
                .gt(WeighRecord::getWeighDate, record.getWeighDate())
                .orderByAsc(WeighRecord::getWeighDate)
                .last("LIMIT 1"));
        if (next == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(record.getWeighDate(), next.getWeighDate());
        next.setGainBaseType("PREV_WEIGH");
        next.setGainBaseDate(record.getWeighDate());
        next.setGainBaseWeight(record.getAvgWeight());
        next.setDailyGain(GrowthCalc.dailyGain(next.getAvgWeight(), record.getAvgWeight(), days));
        if (next.getStdWeight() != null) {
            next.setDeviationRatio(GrowthCalc.deviation(next.getAvgWeight(), next.getStdWeight()));
            next.setGrowthResult(GrowthCalc.growthResult(next.getDeviationRatio(), next.getAllowedDeviation()));
        }
        recordMapper.updateById(next);
    }

    private String updateGrowth(BatchInfo batch, WeighRecord record, boolean latest) {
        WeighRecord latestRecord = latestRecord(batch.batchId());
        BatchGrowth growth = growth(batch);
        String before = growth.getGrowthStatus();
        String after = before;
        if (latestRecord != null && latestRecord.getId().equals(record.getId())) {
            growth.setLastWeighId(record.getId());
            growth.setLastWeighDate(record.getWeighDate());
            growth.setLastAvgWeight(record.getAvgWeight());
            growth.setLastDeviation(record.getDeviationRatio());
            growth.setLastDailyGain(record.getDailyGain());
            growth.setGrowthStatus(record.getGrowthResult());
            if (before == null ? record.getGrowthResult() != null : !before.equals(record.getGrowthResult())) {
                growth.setGrowthStatusAt(BeijingTime.now());
            }
            growth.setNextWeighDate(recalcNextWeighDate(batch, growth));
            after = record.getGrowthResult();
        }
        saveGrowth(batch, growth);
        if (latestRecord != null && latestRecord.getId().equals(record.getId())) {
            if ("SLOW".equals(after) && !"SLOW".equals(before)) {
                return "SET_SLOW";
            }
            if ("FAST".equals(after) && !"FAST".equals(before)) {
                return "SET_FAST";
            }
            if (("SLOW".equals(before) || "FAST".equals(before)) && "NORMAL".equals(after)) {
                return "CLEARED";
            }
        }
        return "UNCHANGED";
    }

    private void saveGrowth(BatchInfo batch, BatchGrowth growth) {
        if (growth.getId() == null) {
            growth.setFarmId(lookup.farmId());
            growth.setBatchId(batch.batchId());
            growth.setVersion(0);
            growthMapper.insert(growth);
        } else if (growthMapper.updateById(growth) == 0) {
            throw FeedErrors.versionConflict();
        }
    }

    private BatchGrowth growth(BatchInfo batch) {
        BatchGrowth growth = growthMapper.selectOne(Wrappers.<BatchGrowth>lambdaQuery()
                .eq(BatchGrowth::getBatchId, batch.batchId()).last("LIMIT 1"));
        if (growth != null) {
            return growth;
        }
        BatchGrowth created = new BatchGrowth();
        created.setFarmId(batch.farmId());
        created.setBatchId(batch.batchId());
        created.setWeighCyclePerMonth(suggestedPerMonth(batch.breedId()));
        created.setCycleCustomized(false);
        created.setGrowthStatus("NONE");
        created.setVersion(0);
        created.setNextWeighDate(defaultNextWeighDate(batch, created.getWeighCyclePerMonth()));
        return created;
    }

    private WeighVO.Cycle cycleVo(BatchInfo batch, BatchGrowth growth) {
        WeighVO.Cycle vo = new WeighVO.Cycle();
        int perMonth = growth.getWeighCyclePerMonth() == null ? suggestedPerMonth(batch.breedId())
                : growth.getWeighCyclePerMonth();
        vo.setPerMonth(perMonth);
        vo.setIntervalDays(GrowthCalc.intervalDays(perMonth));
        vo.setCustomized(growth.getCycleCustomized());
        vo.setSuggestedPerMonth(suggestedPerMonth(batch.breedId()));
        vo.setNextWeighDate(BeijingTime.format(growth.getNextWeighDate() == null
                ? defaultNextWeighDate(batch, perMonth) : growth.getNextWeighDate()));
        return vo;
    }

    private LocalDate recalcNextWeighDate(BatchInfo batch, BatchGrowth growth) {
        int perMonth = growth.getWeighCyclePerMonth() == null ? suggestedPerMonth(batch.breedId())
                : growth.getWeighCyclePerMonth();
        LocalDate base = growth.getLastWeighDate() == null ? batch.entryDate() : growth.getLastWeighDate();
        LocalDate next = base.plusDays(GrowthCalc.intervalDays(perMonth));
        LocalDate today = BeijingTime.today();
        return next.isBefore(today) ? today : next;
    }

    private LocalDate defaultNextWeighDate(BatchInfo batch, Integer perMonth) {
        if (batch.entryDate() == null) {
            return null;
        }
        return batch.entryDate().plusDays(GrowthCalc.intervalDays(perMonth));
    }

    private int suggestedPerMonth(Long breedId) {
        try {
            BreedStandardsVO standards = stdQueryService.standards(breedId, null);
            if (standards != null && standards.getWeight() != null
                    && standards.getWeight().getSuggestedWeighTimes() != null) {
                return standards.getWeight().getSuggestedWeighTimes();
            }
        } catch (RuntimeException ignored) {
            // 没有标准时取 2
        }
        return 2;
    }

    private Standards standards(Long breedId) {
        try {
            BreedStandardsVO standards = stdQueryService.standards(breedId, null);
            if (standards == null || standards.getWeight() == null) {
                return new Standards(null, null, List.of());
            }
            List<WeighVO.StdPoint> points = new ArrayList<>();
            if (standards.getWeight().getPoints() != null) {
                for (BreedStandardsVO.WeightPointVO point : standards.getWeight().getPoints()) {
                    WeighVO.StdPoint vo = new WeighVO.StdPoint();
                    vo.setDayAge(point.getDayAge());
                    vo.setStdWeight(point.getStdWeight());
                    vo.setLower(point.getLower());
                    vo.setUpper(point.getUpper());
                    points.add(vo);
                }
            }
            return new Standards(standards.getWeight().getVersionId(),
                    standards.getWeight().getToleranceRate(), points);
        } catch (RuntimeException e) {
            return new Standards(null, null, List.of());
        }
    }

    private Measurement measure(String mode, Integer sampleCount, BigDecimal totalWeight,
                                List<WeighItemRequest> items, int stock) {
        String normalized = mode == null ? null : mode.trim().toUpperCase();
        if (!"SAMPLE".equals(normalized) && !"INDIVIDUAL".equals(normalized)) {
            throw FeedErrors.field("mode", "请选择录入方式");
        }
        if ("SAMPLE".equals(normalized)) {
            if (sampleCount == null) {
                throw FeedErrors.field("sample_count", "请填写抽样数");
            }
            if (sampleCount < 1) {
                throw FeedErrors.field("sample_count", "抽样数至少 1 头");
            }
            if (stock > 0 && sampleCount > stock) {
                throw FeedErrors.field("sample_count", "不能超过存栏 " + stock + " 头");
            }
            if (totalWeight == null) {
                throw FeedErrors.field("total_weight", "请填写总重");
            }
            if (totalWeight.signum() <= 0) {
                throw FeedErrors.field("total_weight", "总重要大于 0 千克");
            }
            if (totalWeight.scale() > 1) {
                throw FeedErrors.field("total_weight", "总重最多 1 位小数");
            }
            if (totalWeight.compareTo(new BigDecimal("999999.9")) > 0) {
                throw FeedErrors.field("total_weight", "总重不能超过 999999.9 千克");
            }
            BigDecimal avg = totalWeight.divide(BigDecimal.valueOf(sampleCount), 2, RoundingMode.HALF_UP);
            return new Measurement(normalized, sampleCount, totalWeight, avg, List.of());
        }
        if (items == null || items.isEmpty()) {
            throw FeedErrors.field("items", "请填写逐头体重");
        }
        if (stock > 0 && items.size() > stock) {
            throw FeedErrors.field("items", "录入头数不能超过存栏 " + stock + " 头");
        }
        BigDecimal total = BigDecimal.ZERO;
        List<BigDecimal> weights = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BigDecimal weight = items.get(i) == null ? null : items.get(i).getWeight();
            if (weight == null) {
                throw FeedErrors.field("items[" + i + "].weight", "请填写第 " + (i + 1) + " 头的体重");
            }
            if (weight.signum() <= 0) {
                throw FeedErrors.field("items[" + i + "].weight", "体重要大于 0 千克");
            }
            if (weight.scale() > 1) {
                throw FeedErrors.field("items[" + i + "].weight", "体重最多 1 位小数");
            }
            if (weight.compareTo(new BigDecimal("9999.9")) > 0) {
                throw FeedErrors.field("items[" + i + "].weight", "体重不能超过 9999.9 千克");
            }
            total = total.add(weight);
            weights.add(weight);
        }
        BigDecimal avg = total.divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
        return new Measurement(normalized, items.size(), total, avg, weights);
    }

    private Gain gain(BatchInfo batch, LocalDate date, BigDecimal avgWeight) {
        WeighRecord previous = lastBefore(batch.batchId(), date);
        if (previous != null) {
            long days = ChronoUnit.DAYS.between(previous.getWeighDate(), date);
            return new Gain("PREV_WEIGH", previous.getWeighDate(), previous.getAvgWeight(),
                    GrowthCalc.dailyGain(avgWeight, previous.getAvgWeight(), days));
        }
        if (lookup.entryAvgWeight(batch.batchId()) != null && batch.entryDate() != null) {
            long days = ChronoUnit.DAYS.between(batch.entryDate(), date);
            return new Gain("INTAKE", batch.entryDate(), lookup.entryAvgWeight(batch.batchId()),
                    GrowthCalc.dailyGain(avgWeight, lookup.entryAvgWeight(batch.batchId()), days));
        }
        return new Gain("NONE", null, null, null);
    }

    private void saveItems(WeighRecord record, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            return;
        }
        for (int i = 0; i < weights.size(); i++) {
            WeighRecordItem item = new WeighRecordItem();
            item.setFarmId(record.getFarmId());
            item.setWeighRecordId(record.getId());
            item.setSeqNo(i + 1);
            item.setWeight(weights.get(i));
            itemMapper.insert(item);
        }
    }

    private List<WeighRecord> recordsOf(Long batchId) {
        List<WeighRecord> records = recordMapper.selectList(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, batchId)
                .orderByAsc(WeighRecord::getWeighDate));
        return records;
    }

    private WeighRecord latestRecord(Long batchId) {
        List<WeighRecord> records = recordMapper.selectList(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, batchId)
                .orderByDesc(WeighRecord::getWeighDate)
                .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }

    private WeighRecord lastBefore(Long batchId, LocalDate date) {
        List<WeighRecord> records = recordMapper.selectList(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, batchId)
                .lt(WeighRecord::getWeighDate, date)
                .orderByDesc(WeighRecord::getWeighDate)
                .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }

    private WeighRecord recordByDate(Long batchId, LocalDate date) {
        return recordMapper.selectOne(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, batchId)
                .eq(WeighRecord::getWeighDate, date)
                .last("LIMIT 1"));
    }

    private WeighRecord requireRecord(Long id) {
        if (id == null) {
            throw FeedErrors.notFound();
        }
        WeighRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw FeedErrors.notFound();
        }
        return record;
    }

    private void validateDate(BatchInfo batch, LocalDate date) {
        if (date.isAfter(BeijingTime.today())) {
            throw FeedErrors.field("weigh_date", "称重日期不能晚于今天");
        }
        if (batch.entryDate() != null && date.isBefore(batch.entryDate())) {
            throw FeedErrors.field("weigh_date", "称重日期不能早于入栏日期 " + monthDay(batch.entryDate()));
        }
    }

    private int stockCount(BatchInfo batch, LocalDate date) {
        return batch.currentCount() == null ? 0 : batch.currentCount();
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), BeijingTime.DATE);
        } catch (DateTimeParseException e) {
            throw FeedErrors.field("weigh_date", "称重日期格式应为 yyyy-MM-dd");
        }
    }

    private WeighVO.Batch toBatch(BatchInfo batch) {
        WeighVO.Batch vo = new WeighVO.Batch();
        vo.setBatchId(batch.batchId());
        vo.setBatchNo(batch.batchNo());
        vo.setPenNames(lookup.penNames(batch.penIds()));
        vo.setBreedName(lookup.breedName(batch.breedId()));
        vo.setDayAge(FeedCalc.dayAge(batch, BeijingTime.today()));
        vo.setCurrentCount(batch.currentCount());
        vo.setEntryDate(BeijingTime.format(batch.entryDate()));
        vo.setEntryAgeDays(batch.entryAgeDays());
        vo.setEntryAvgWeight(lookup.entryAvgWeight(batch.batchId()));
        vo.setStatus(batch.status());
        return vo;
    }

    private BatchInfo synthetic(FeedBatchRow row) {
        return new BatchInfo(row.getBatchId() == null ? null : farmIdOf(row),
                row.getBatchId(), row.getBatchNo(), row.getBreedId(), row.getCategoryId(), row.getEntryDate(),
                row.getCurrentCount(), row.getStatus(), List.of(), row.getEntryAgeDays(), row.getEntryCount(),
                0, 0, null);
    }

    private Long farmIdOf(FeedBatchRow row) {
        return lookup.farmId();
    }

    private static String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private static FarmContext.FarmMember context() {
        return FarmContext.require();
    }

    private record Standards(Long versionId, BigDecimal tolerance, List<WeighVO.StdPoint> points) {
    }

    private record CompareRow(WeighVO.CompareOption vo, LocalDate lastWeighDate) {
    }

    private record Measurement(String mode, int sampleCount, BigDecimal totalWeight, BigDecimal avgWeight,
                               List<BigDecimal> items) {
    }

    private record Gain(String type, LocalDate baseDate, BigDecimal baseWeight, BigDecimal dailyGain) {
    }
}
