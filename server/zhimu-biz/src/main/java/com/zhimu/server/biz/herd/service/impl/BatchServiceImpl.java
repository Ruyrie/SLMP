package com.zhimu.server.biz.herd.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.base.api.BreedQueryService;
import com.zhimu.server.admin.base.api.CategoryInfo;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.farm.entity.FarmMember;
import com.zhimu.server.biz.farm.support.FarmLookup;
import com.zhimu.server.biz.herd.api.BatchTimelineProvider;
import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.entity.BatchEvent;
import com.zhimu.server.biz.herd.entity.BatchPen;
import com.zhimu.server.biz.herd.entity.BatchTimeline;
import com.zhimu.server.biz.herd.entity.Pen;
import com.zhimu.server.biz.herd.mapper.BatchEventMapper;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.biz.herd.mapper.BatchPenMapper;
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.mapper.PenMapper;
import com.zhimu.server.biz.herd.query.BatchListQuery;
import com.zhimu.server.biz.herd.query.BatchLookupQuery;
import com.zhimu.server.biz.herd.query.TimelineQuery;
import com.zhimu.server.biz.herd.request.BatchCreateRequest;
import com.zhimu.server.biz.herd.request.DeathCreateRequest;
import com.zhimu.server.biz.herd.request.DeathUpdateRequest;
import com.zhimu.server.biz.herd.request.EntryUpdateRequest;
import com.zhimu.server.biz.herd.request.TransferCreateRequest;
import com.zhimu.server.biz.herd.request.TransferUpdateRequest;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.health.service.MedicationService;
import com.zhimu.server.biz.herd.support.BatchAccess;
import com.zhimu.server.biz.herd.support.BatchNoGenerator;
import com.zhimu.server.biz.herd.support.BatchQrLinks;
import com.zhimu.server.biz.herd.support.HerdErrors;
import com.zhimu.server.biz.herd.support.HerdSupport;
import com.zhimu.server.biz.herd.support.QrCodes;
import com.zhimu.server.biz.herd.vo.BatchDetailVO;
import com.zhimu.server.biz.herd.vo.BatchEntryResultVO;
import com.zhimu.server.biz.herd.vo.BatchEventDetailVO;
import com.zhimu.server.biz.herd.vo.BatchListItemVO;
import com.zhimu.server.biz.herd.vo.BatchLookupVO;
import com.zhimu.server.biz.herd.vo.BatchPenItem;
import com.zhimu.server.biz.herd.vo.DeathResultVO;
import com.zhimu.server.biz.herd.vo.PenStockVO;
import com.zhimu.server.biz.herd.vo.RequestCheckVO;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.herd.vo.TransferResultVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** API-L06、L14–L17、L19、L26 查询实现。 */
@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    private static final Pattern BATCH_NO = Pattern.compile("^ZM\\d{4}-\\d{3,}$");
    private static final JsonMapper JSON = com.zhimu.server.framework.jackson.ZhimuJackson.newPlainMapper();

    private final HerdSupport support;
    private final BatchAccess access;
    private final BatchMapper batchMapper;
    private final BatchEventMapper eventMapper;
    private final BatchTimelineMapper timelineMapper;
    private final BatchPenMapper batchPenMapper;
    private final PenMapper penMapper;
    private final BatchNoGenerator batchNoGenerator;
    private final FarmLookup farmLookup;
    private final FarmMemberCache memberCache;
    private final BizMediaService bizMediaService;
    private final OperationLogRecorder operationLogRecorder;
    private final ObjectProvider<BatchTimelineProvider> timelineProviders;
    private final MedicationService medicationService;
    private final com.zhimu.server.biz.common.api.ChangeRequestService changeRequestService;
    private final BatchQrLinks qrLinks;
    @Override
    public PageVO<BatchListItemVO> list(BatchListQuery query) {
        FarmContext.FarmMember member = support.context();
        Long farmId = member.farmId();
        String status = query.getStatus() == null || query.getStatus().isBlank()
                ? "ACTIVE" : query.getStatus().toUpperCase();
        if (member.isStaff()) {
            status = "ACTIVE";
        }
        String keyword = trim(query.getKeyword());
        List<Batch> batches = support.batches(farmId, status);
        if (member.isStaff()) {
            batches = batches.stream().filter(batch -> support.visibleToStaff(batch, member)).toList();
        }
        Map<Long, Pen> penMap = support.penMap(support.pensOfFarm(farmId).stream().map(Pen::getId)
                .collect(java.util.stream.Collectors.toSet()));
        List<BatchListItemVO> items = new ArrayList<>();
        for (Batch batch : batches) {
            List<BatchPen> rows = support.positivePensOfBatch(batch.getId());
            if (!matches(batch, rows, penMap, query, keyword)) {
                continue;
            }
            items.add(support.batchItem(batch, rows, penMap));
        }
        if ("DAY_AGE".equalsIgnoreCase(query.getSort())) {
            items.sort(Comparator.comparing(BatchListItemVO::getDayAge,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else if ("ENTRY_DATE".equalsIgnoreCase(query.getSort())) {
            items.sort(Comparator.comparing(BatchListItemVO::getEntryDate,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            items.sort(Comparator.comparing(BatchListItemVO::getBatchId));
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        PageVO<BatchListItemVO> result = PageVO.of(new ArrayList<>(items.subList(from, to)), items.size(), page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        if (member.isStaff()) {
            tabs.put("ACTIVE", visibleBatchCount(farmId, "ACTIVE", member));
            tabs.put("ENDED", visibleBatchCount(farmId, "ENDED", member));
            tabs.put("ALL", visibleBatchCount(farmId, "ALL", member));
        } else {
            tabs.put("ACTIVE", (int) support.countBatches(farmId, "ACTIVE"));
            tabs.put("ENDED", (int) support.countBatches(farmId, "ENDED"));
            tabs.put("ALL", (int) support.countBatches(farmId, "ALL"));
        }
        result.setTabCounts(tabs);
        return result;
    }

    @Override
    public BatchLookupVO lookup(BatchLookupQuery query, Long currentFarmId) {
        String qrContent = trim(query.getQrContent());
        String batchNo = trim(query.getBatchNo());
        if (qrContent == null && batchNo == null) {
            throw HerdErrors.fieldInvalid("batch_no", "请输入批次号");
        }
        Batch batch;
        if (qrContent != null) {
            String token = qrLinks.parseToken(qrContent);
            if (token == null) {
                return lookupResult(BatchLookupVO.NOT_BATCH_QR, "没有识别到批次二维码，请对准批次标签重试");
            }
            batch = TenantSkip.call(() -> batchMapper.selectByQrTokenIncludingDeleted(token));
            if (batch != null && !token.equals(batch.getQrToken())) batch = null;
        } else {
            String normalized = batchNo.toUpperCase();
            if (!BATCH_NO.matcher(normalized).matches()) {
                return lookupResult(BatchLookupVO.INVALID_FORMAT, "批次号格式不正确，例如 ZM2610-031");
            }
            batch = TenantSkip.call(() -> batchMapper.selectByBatchNoIncludingDeleted(normalized));
        }
        if (batch == null || Boolean.TRUE.equals(batch.getDeleted())) {
            String message = batchNo == null
                    ? "没有找到这个批次，请核对批次标签"
                    : "没有找到批次 " + batchNo.toUpperCase() + "，请核对批次号";
            return lookupResult(BatchLookupVO.NOT_FOUND, message);
        }
        Long userId = com.zhimu.server.common.context.UserContext.requireUserId();
        FarmContext.FarmMember target = memberCache.get(batch.getFarmId(), userId);
        if (target == null || target.farmStatus() == FarmStatus.PENDING || target.farmStatus() == FarmStatus.REJECTED) {
            if (qrContent != null && Boolean.TRUE.equals(batch.getTraceEnabled())) return publicTrace(batch);
            return lookupResult(BatchLookupVO.NO_PERMISSION,
                    "你没有这个批次的查看权限。这个批次不属于你加入的养殖场，或不在你负责的栏舍内。");
        }
        if (!support.visibleToStaff(batch, target)) {
            if (qrContent != null && Boolean.TRUE.equals(batch.getTraceEnabled())) return publicTrace(batch);
            return lookupResult(BatchLookupVO.NO_PERMISSION,
                    "你没有这个批次的查看权限。这个批次不属于你加入的养殖场，或不在你负责的栏舍内。");
        }
        BatchLookupVO vo = new BatchLookupVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setFarmId(batch.getFarmId());
        vo.setFarmName(target.farmName());
        vo.setRole(target.role() == null ? null : target.role().name());
        if (currentFarmId != null && currentFarmId.equals(batch.getFarmId())) {
            vo.setResult(BatchLookupVO.OK);
        } else {
            vo.setResult(BatchLookupVO.SWITCH_FARM);
            vo.setMessage("已切换到" + target.farmName());
        }
        return vo;
    }

    private BatchLookupVO publicTrace(Batch batch) {
        var farm = farmLookup.findFarm(batch.getFarmId());
        if (farm == null || !"APPROVED".equals(farm.getAuditStatus()) || Boolean.TRUE.equals(farm.getDisabled())
                || Boolean.TRUE.equals(farm.getDeleted()) || Boolean.TRUE.equals(farm.getFrozen()))
            return lookupResult(BatchLookupVO.NOT_FOUND, "该批次暂未开放公开溯源");
        BatchLookupVO result = lookupResult(BatchLookupVO.PUBLIC_TRACE, "查看公开溯源");
        result.setTraceToken(batch.getQrToken());
        return result;
    }

    @Override
    public BatchDetailVO detail(Long batchId) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(batchId);
        List<BatchPen> penRows = support.positivePensOfBatch(batch.getId());
        Map<Long, Pen> penMap = support.penMap(penRows.stream().map(BatchPen::getPenId)
                .collect(java.util.stream.Collectors.toSet()));
        BatchEntryResultVO ignored = null;
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatchId(batch.getId());
        vo.setFarmId(batch.getFarmId());
        vo.setCategoryId(batch.getCategoryId());
        vo.setBreedId(batch.getBreedId());
        BatchEvent entry = eventMapper.selectEntry(batch.getId());
        vo.setEntryEventId(entry == null ? 0L : entry.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setFarmName(member.farmName());
        vo.setCategoryName(support.categoryName(batch.getCategoryId()));
        vo.setBreedName(support.breedName(batch.getBreedId()));
        vo.setBreedTitle(support.breedTitle(batch));
        vo.setPurpose(batch.getPurpose());
        vo.setSource(batch.getSource());
        vo.setStatus(batch.getStatus());
        vo.setEntryDate(BeijingTime.format(batch.getEntryDate()));
        vo.setQrContent(qrLinks.content(batch.getQrToken()));
        vo.setTraceEnabled(!Boolean.FALSE.equals(batch.getTraceEnabled()));
        vo.setSourceRecordType(batch.getSourceRecordType());
        vo.setEndType(batch.getEndType());
        vo.setEndDate(BeijingTime.format(batch.getEndDate()));
        vo.setStageName(support.stageName(batch));
        List<BatchPenItem> pens = new ArrayList<>();
        Pen labelPen = null;
        for (BatchPen row : penRows) {
            Pen pen = penMap.get(row.getPenId());
            BatchPenItem item = new BatchPenItem();
            item.setPenId(row.getPenId());
            item.setPenName(pen == null ? null : pen.getPenName());
            item.setStockCount(row.getStockCount());
            pens.add(item);
            if (labelPen == null && pen != null) {
                labelPen = pen;
            }
        }
        vo.setPens(pens);
        if (batch.getEntryPenId() != null) {
            Pen entryPen = support.pensByIds(List.of(batch.getEntryPenId())).stream().findFirst().orElse(null);
            if (entryPen != null) {
                vo.setEntryPen(PenBriefVO.builder().penId(entryPen.getId()).name(entryPen.getPenName())
                        .penTypeText(support.penTypeText(entryPen.getPenType())).build());
            }
        }
        vo.setDayAge(support.dayAge(batch));
        vo.setDaysRaised(support.daysRaised(batch));
        vo.setCurrentCount(batch.getCurrentCount());
        vo.setEntryCount(batch.getEntryCount());
        vo.setDeathCount(batch.getDeathCount());
        vo.setSoldCount(batch.getSoldCount());
        vo.setEntryAgeDays(batch.getEntryAgeDays());
        vo.setVersion(batch.getVersion());
        vo.setMortalityRate(HerdSupport.mortality(batch.getDeathCount(), batch.getEntryCount()));
        vo.setEntryAvgWeight(batch.getEntryAvgWeight());
        vo.setIntroUnitPrice(batch.getIntroUnitPrice());
        // 公共修改申请：本批次待审核的申请数（任何提交人）
        vo.setPendingChangeRequestCount((int) changeRequestService.countPendingByBatch(batch.getFarmId(), batchId));
        BatchDetailVO.WithdrawalInfo withdrawal = new BatchDetailVO.WithdrawalInfo();
        var health = medicationService.withdrawal(batchId);
        withdrawal.setInWithdrawal("IN_WITHDRAWAL".equals(health.getStatus()));
        withdrawal.setRemainingDays(health.getDaysLeft());
        withdrawal.setWithdrawalDays(health.getWithdrawalDays());
        withdrawal.setSellableDate(health.getReleaseDate() == null ? null : health.getReleaseDate().toString());
        withdrawal.setLastDrugName(health.getDecisiveDrugName());
        withdrawal.setLastDrugDate(health.getLastMedDate() == null ? null : health.getLastMedDate().toString());
        vo.setWithdrawal(withdrawal);
        vo.setActions(actions(batch, member));
        String unit = support.farmUnit(batch.getFarmId());
        vo.setUnit(unit);
        vo.setUnitText(support.unitText(unit));
        String penName = labelPen == null ? support.pensByIds(List.of(batch.getEntryPenId())).stream()
                .findFirst().map(Pen::getPenName).orElse("") : labelPen.getPenName();
        vo.setLabelText(member.farmName() + " · " + penName + " · " + support.breedName(batch.getBreedId()));
        return vo;
    }

    @Override
    public PageVO<TimelineItemVO> timeline(Long batchId, TimelineQuery query) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(batchId);
        List<BatchTimeline> rows = timelineMapper.selectList(Wrappers.<BatchTimeline>lambdaQuery()
                .eq(BatchTimeline::getBatchId, batchId)
                .eq(BatchTimeline::getDeleted, false)
                .orderByDesc(BatchTimeline::getOccurredAt)
                .orderByDesc(BatchTimeline::getId));
        String type = query.getType() == null ? "ALL" : query.getType().toUpperCase();
        Set<Long> operatorIds = new LinkedHashSet<>();
        for (BatchTimeline row : rows) {
            if (row.getOperatorUserId() != null) {
                operatorIds.add(row.getOperatorUserId());
            }
        }
        Map<Long, String> names = support.userNames(operatorIds);
        boolean manager = HerdSupport.isManager(member);
        boolean hasFollowUp = batchMapper.countFollowUpEvents(batchId) > 0;
        List<TimelineItemVO> items = new ArrayList<>();
        for (BatchTimeline row : rows) {
            if (!matchType(type, row.getRecordType())) {
                continue;
            }
            TimelineItemVO item = new TimelineItemVO();
            item.setId(row.getId());
            item.setBizId(row.getBizId());
            item.setRecordType(row.getRecordType());
            item.setBizType(row.getBizType());
            item.setBizDate(BeijingTime.format(row.getBizDate()));
            item.setOccurredAt(BeijingTime.format(row.getOccurredAt()));
            item.setTitle(row.getTitle());
            item.setSubtitle(row.getSubtitle());
            item.setIsToday(BeijingTime.today().equals(row.getBizDate()));
            List<TimelineItemVO.KeyValue> detail = parseDetail(row.getDetailJson());
            item.setDetail(detail);
            item.setExpandable(!detail.isEmpty());
            item.setOperatorName(row.getOperatorUserId() == null ? null : names.get(row.getOperatorUserId()));
            applyTimelineAction(item, batch, row, member, manager, hasFollowUp);
            items.add(item);
        }
        List<BatchTimelineProvider> providers = timelineProviders == null
                ? List.of() : timelineProviders.orderedStream().toList();
        if (!providers.isEmpty()) {
            for (BatchTimelineProvider provider : providers) {
                List<TimelineItemVO> extra = provider.extraRecords(batch.getFarmId(), batchId);
                if (extra != null && !extra.isEmpty()) {
                    extra.stream().filter(row -> matchType(type, row.getRecordType())).forEach(items::add);
                }
            }
            items.sort(Comparator.comparing(TimelineItemVO::getOccurredAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(TimelineItemVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int from = Math.min((page - 1) * size, items.size());
        int to = Math.min(from + size, items.size());
        return PageVO.of(new ArrayList<>(items.subList(from, to)), items.size(), page, size);
    }

    @Override
    public List<PenStockVO> penStocks(Long batchId) {
        FarmContext.FarmMember member = support.context();
        access.require(batchId);
        List<BatchPen> rows = support.positivePensOfBatch(batchId);
        Map<Long, Pen> penMap = support.penMap(rows.stream().map(BatchPen::getPenId)
                .collect(java.util.stream.Collectors.toSet()));
        List<PenStockVO> result = new ArrayList<>();
        for (BatchPen row : rows) {
            if (member.isStaff() && !member.canAccessPen(row.getPenId())) {
                continue;
            }
            Pen pen = penMap.get(row.getPenId());
            PenStockVO vo = new PenStockVO();
            vo.setPenId(row.getPenId());
            vo.setPenName(pen == null ? null : pen.getPenName());
            vo.setFirstInDate(BeijingTime.format(row.getFirstInDate()));
            vo.setStockCount(row.getStockCount());
            vo.setCapacity(pen == null ? null : pen.getCapacity());
            vo.setUnitText(support.unitText(support.farmUnit(member.farmId())));
            result.add(vo);
        }
        return result;
    }

    @Override
    public BatchEventDetailVO eventDetail(Long eventId) {
        BatchEvent event = eventId == null ? null : eventMapper.selectById(eventId);
        if (event == null) {
            throw HerdErrors.notFound();
        }
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(event.getBatchId());
        BatchEventDetailVO vo = new BatchEventDetailVO();
        vo.setEventId(event.getId());
        vo.setBatchId(event.getBatchId());
        vo.setEventType(event.getEventType());
        vo.setStatus(event.getStatus());
        vo.setBatchNo(batch.getBatchNo());
        vo.setBreedTitle(support.breedTitle(batch));
        vo.setEventDate(BeijingTime.format(event.getEventDate()));
        vo.setCreatedAt(BeijingTime.format(event.getCreatedAt()));
        vo.setQuantity(event.getQuantity());
        vo.setOverCapacityCount(event.getOverCapacityCount());
        vo.setVersion(event.getVersion());
        vo.setCapacityConfirmed(Boolean.TRUE.equals(event.getCapacityConfirmed()));
        vo.setPen(penBrief(event.getPenId()));
        vo.setToPen(penBrief(event.getToPenId()));
        vo.setReasonCode(event.getReasonCode());
        vo.setRemark(event.getRemark());
        vo.setLastEditedAt(event.getUpdatedBy() == null ? null : BeijingTime.format(event.getUpdatedAt()));
        vo.setExecutor(userBrief(event.getExecutorUserId()));
        vo.setRegistrant(userBrief(event.getCreatedBy()));
        vo.setLastEditor(userBrief(event.getUpdatedBy()));
        List<com.zhimu.server.common.vo.MediaItemVO> photos =
                bizMediaService.list(mediaType(event.getEventType()), event.getId());
        vo.setPhotos(photos == null ? List.of() : photos);
        vo.setChangeState(changeState(event, member, batch));
        if ("ENTRY".equals(event.getEventType())) {
            BatchEventDetailVO.EntryInfo entryInfo = new BatchEventDetailVO.EntryInfo();
            entryInfo.setSource(batch.getSource());
            entryInfo.setPurpose(batch.getPurpose());
            entryInfo.setBreedId(batch.getBreedId());
            entryInfo.setBreedName(support.breedName(batch.getBreedId()));
            entryInfo.setEntryAgeDays(batch.getEntryAgeDays());
            entryInfo.setEntryAvgWeight(batch.getEntryAvgWeight());
            entryInfo.setIntroUnitPrice(batch.getIntroUnitPrice());
            entryInfo.setBatchVersion(batch.getVersion());
            vo.setEntry(entryInfo);
        }
        return vo;
    }

    @Override
    public RequestCheckVO checkByRequest(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw HerdErrors.fieldInvalid("request_key", "请求编号不能为空");
        }
        if (requestKey.length() > 64) {
            throw HerdErrors.fieldInvalid("request_key", "请求编号最多 64 位");
        }
        Long userId = com.zhimu.server.common.context.UserContext.requireUserId();
        RequestCheckVO vo = new RequestCheckVO();
        BatchEvent event = eventMapper.selectByRequest(userId, requestKey.trim());
        if (event == null) {
            vo.setExists(false);
            return vo;
        }
        vo.setExists(true);
        vo.setEventType(event.getEventType());
        vo.setEventId(event.getId());
        vo.setBatchId(event.getBatchId());
        Batch batch = TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(event.getBatchId()));
        if (batch != null) {
            vo.setResult(resultOf(event, batch));
        }
        return vo;
    }

    // ===== 写接口 =====

    @Override
    @Transactional
    public BatchEntryResultVO create(BatchCreateRequest request, String requestKey) {
        FarmContext.FarmMember member = support.context();
        Long farmId = member.farmId();
        Pen pen = requirePenInFarm(farmId, request.getPenId(), "pen_id", "请选择栏舍");
        if (!"ENABLED".equals(pen.getStatus())) {
            throw HerdErrors.penDisabled("这个栏舍已停用，不能入栏");
        }
        BreedQueryService.BreedInfo breed = support.breed(request.getBreedId());
        if (breed == null || !breed.enabled()) {
            throw HerdErrors.fieldInvalid("breed_id", "请选择品种");
        }
        CategoryInfo category = support.category(breed.categoryId());
        if (category == null) {
            throw HerdErrors.fieldInvalid("breed_id", "请选择品种");
        }
        String source = upper(request.getSource());
        if (!"PURCHASED".equals(source) && !"SELF_BRED".equals(source)) {
            throw HerdErrors.fieldInvalid("source", "请选择来源");
        }
        LocalDate entryDate = request.getEntryDate();
        if (entryDate == null) {
            throw HerdErrors.fieldInvalid("entry_date", "请选择入栏日期");
        }
        if (entryDate.isAfter(BeijingTime.today())) {
            throw HerdErrors.fieldInvalid("entry_date", "入栏日期不能晚于今天");
        }
        int age = requireEntryAge(request.getEntryAgeDays());
        int count = requireCount(request.getEntryCount(), "entry_count");
        BigDecimal weight = validateWeight(request.getEntryAvgWeight());
        BigDecimal price = validatePrice(request.getIntroUnitPrice(), source);
        String purpose = request.getPurpose() == null || request.getPurpose().isBlank()
                ? (Boolean.TRUE.equals(category.isBreeding()) ? "BREEDING" : "COMMERCIAL")
                : request.getPurpose().toUpperCase();
        if (!"COMMERCIAL".equals(purpose) && !"BREEDING".equals(purpose)) {
            throw HerdErrors.fieldInvalid("purpose", "请选择用途");
        }
        ensurePenCategory(pen, category.id(), null);
        int before = pen.getStockCount() == null ? 0 : pen.getStockCount();
        int after = before + count;
        int over = Math.max(0, after - capacityOf(pen));
        if (over > 0 && !Boolean.TRUE.equals(request.getCapacityConfirmed())) {
            throw HerdErrors.capacityConfirm(pen.getId(), pen.getPenName(), "入栏", capacityOf(pen), before, after, over);
        }

        BatchNoGenerator.Generated generated = batchNoGenerator.next();
        Batch batch = new Batch();
        batch.setFarmId(farmId);
        batch.setBatchNo(generated.batchNo());
        batch.setNoMonth(generated.month());
        batch.setNoSeq(generated.seq());
        batch.setQrToken(QrCodes.nextToken());
        batch.setCategoryId(category.id());
        batch.setBreedId(breed.id());
        batch.setPurpose(purpose);
        batch.setSource(source);
        batch.setEntryPenId(pen.getId());
        batch.setEntryDate(entryDate);
        batch.setEntryAgeDays(age);
        batch.setEntryCount(count);
        batch.setEntryAvgWeight(weight);
        batch.setIntroUnitPrice("PURCHASED".equals(source) ? price : null);
        batch.setCurrentCount(count);
        batch.setDeathCount(0);
        batch.setSoldCount(0);
        batch.setStatus("ACTIVE");
        batch.setClientRequestId(requestKey);
        batch.setVersion(0);
        batch.setDeleted(false);
        batch.setUpdatedBy(member.userId());
        batchMapper.insert(batch);

        BatchPen batchPen = new BatchPen();
        batchPen.setFarmId(farmId);
        batchPen.setBatchId(batch.getId());
        batchPen.setPenId(pen.getId());
        batchPen.setStockCount(count);
        batchPen.setFirstInDate(entryDate);
        batchPenMapper.insert(batchPen);

        BatchEvent event = new BatchEvent();
        event.setFarmId(farmId);
        event.setBatchId(batch.getId());
        event.setEventType("ENTRY");
        event.setEventDate(entryDate);
        event.setPenId(pen.getId());
        event.setQuantity(count);
        event.setExecutorUserId(member.userId());
        event.setCapacityConfirmed(over > 0);
        event.setOverCapacityCount(over);
        event.setStatus("EFFECTIVE");
        event.setClientRequestId(requestKey);
        event.setVersion(0);
        event.setDeleted(false);
        eventMapper.insert(event);

        penMapper.addStock(pen.getId(), count, BeijingTime.now());
        writeTimeline(batch, pen.getId(), "ENTRY", BizType.BATCH_ENTRY.name(), event.getId(), entryDate,
                "入栏 " + support.countText(batch, count), sourceText(source) + " · 入栏日龄 " + age + " 天 · " + member.farmName(),
                detail("登记人", support.userName(member.userId()), "登记时间", BeijingTime.format(entryDate),
                        "入栏平均体重", weight == null ? null : weight.stripTrailingZeros().toPlainString() + " 千克"),
                member.userId());

        BatchEntryResultVO vo = new BatchEntryResultVO();
        vo.setBatchId(batch.getId());
        vo.setPenId(pen.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setQrContent(qrLinks.content(batch.getQrToken()));
        vo.setFarmName(member.farmName());
        vo.setPenName(pen.getPenName());
        vo.setBreedName(support.breedName(breed.id()));
        vo.setPurpose(purpose);
        vo.setEntryDate(BeijingTime.format(entryDate));
        vo.setEntryCount(count);
        vo.setEntryAgeDays(age);
        vo.setPenStockAfter(after);
        vo.setCapacity(capacityOf(pen));
        vo.setOverCapacityCount(over);
        vo.setUnitText(support.unitText(support.farmUnit(farmId)));
        vo.setLabelText(member.farmName() + " · " + pen.getPenName() + " · " + vo.getBreedName());
        return vo;
    }

    @Override
    @Transactional
    public BatchDetailVO updateEntry(Long batchId, EntryUpdateRequest request) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(batchId);
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        if (batchMapper.countFollowUpEvents(batchId) > 0) {
            throw HerdErrors.changeRequired("这条入栏记录已有后续记录，需要提交修改申请");
        }
        if (request.getVersion() == null || !request.getVersion().equals(batch.getVersion())) {
            throw HerdErrors.versionConflict();
        }
        Pen newPen = requirePenInFarm(member.farmId(), request.getPenId(), "pen_id", "请选择栏舍");
        if (!"ENABLED".equals(newPen.getStatus())) {
            throw HerdErrors.penDisabled("这个栏舍已停用，不能入栏");
        }
        BreedQueryService.BreedInfo breed = support.breed(request.getBreedId());
        if (breed == null || !breed.enabled()) {
            throw HerdErrors.fieldInvalid("breed_id", "请选择品种");
        }
        CategoryInfo category = support.category(breed.categoryId());
        if (category == null) {
            throw HerdErrors.fieldInvalid("breed_id", "请选择品种");
        }
        LocalDate entryDate = request.getEntryDate();
        if (entryDate == null || entryDate.isAfter(BeijingTime.today())) {
            throw HerdErrors.fieldInvalid("entry_date", "入栏日期不能晚于今天");
        }
        int age = requireEntryAge(request.getEntryAgeDays());
        int newCount = requireCount(request.getEntryCount(), "entry_count");
        BigDecimal weight = validateWeight(request.getEntryAvgWeight());
        String source = batch.getSource();
        BigDecimal price = validatePrice(request.getIntroUnitPrice(), source);
        ensurePenCategory(newPen, category.id(), batchId);

        List<BatchPen> rows = support.positivePensOfBatch(batchId);
        BatchPen current = rows.isEmpty() ? null : rows.get(0);
        Long oldPenId = current == null ? batch.getEntryPenId() : current.getPenId();
        int oldStock = current == null ? 0 : current.getStockCount();
        boolean samePen = newPen.getId().equals(oldPenId);
        int penBefore = (newPen.getStockCount() == null ? 0 : newPen.getStockCount()) - (samePen ? oldStock : 0);
        int after = penBefore + newCount;
        int over = Math.max(0, after - capacityOf(newPen));
        if (over > 0 && !Boolean.TRUE.equals(request.getCapacityConfirmed())) {
            throw HerdErrors.capacityConfirm(newPen.getId(), newPen.getPenName(), "入栏", capacityOf(newPen), penBefore,
                    after, over);
        }
        int oldEntryCount = batch.getEntryCount() == null ? 0 : batch.getEntryCount();
        int newCurrent = (batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()) + (newCount - oldEntryCount);
        if (newCurrent < 0) {
            throw HerdErrors.countExceeded(newPen.getId(), newPen.getPenName(), oldEntryCount, null);
        }
        if (samePen) {
            int delta = newCount - oldStock;
            if (current == null) {
                if (batchPenMapper.addStock(batchId, newPen.getId(), newCount, entryDate, BeijingTime.now()) == 0) {
                    insertBatchPen(batch, newPen.getId(), newCount, entryDate);
                }
            } else {
                current.setStockCount(newCount);
                batchPenMapper.updateById(current);
            }
            if (delta != 0) {
                penMapper.addStock(newPen.getId(), delta, BeijingTime.now());
            }
        } else {
            if (current != null) {
                current.setStockCount(0);
                current.setLastOutDate(entryDate);
                batchPenMapper.updateById(current);
                penMapper.addStock(oldPenId, -oldStock, BeijingTime.now());
            }
            if (batchPenMapper.addStock(batchId, newPen.getId(), newCount, entryDate, BeijingTime.now()) == 0) {
                insertBatchPen(batch, newPen.getId(), newCount, entryDate);
            }
            penMapper.addStock(newPen.getId(), newCount, BeijingTime.now());
        }
        int rowsUpdated = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batchId)
                .eq(Batch::getVersion, request.getVersion())
                .set(Batch::getEntryPenId, newPen.getId())
                .set(Batch::getEntryDate, entryDate)
                .set(Batch::getEntryAgeDays, age)
                .set(Batch::getEntryCount, newCount)
                .set(Batch::getEntryAvgWeight, weight)
                .set(Batch::getIntroUnitPrice, "PURCHASED".equals(source) ? price : null)
                .set(Batch::getBreedId, breed.id())
                .set(Batch::getCategoryId, category.id())
                .set(Batch::getCurrentCount, newCurrent)
                .set(Batch::getVersion, request.getVersion() + 1)
                .set(Batch::getUpdatedBy, member.userId())
                .set(Batch::getUpdatedAt, BeijingTime.now()));
        if (rowsUpdated == 0) {
            throw HerdErrors.versionConflict();
        }
        BatchEvent entry = eventMapper.selectEntry(batchId);
        if (entry != null) {
            eventMapper.update(null, Wrappers.<BatchEvent>lambdaUpdate()
                    .eq(BatchEvent::getId, entry.getId())
                    .set(BatchEvent::getPenId, newPen.getId())
                    .set(BatchEvent::getEventDate, entryDate)
                    .set(BatchEvent::getQuantity, newCount)
                    .set(BatchEvent::getOverCapacityCount, over)
                    .set(BatchEvent::getVersion, entry.getVersion() + 1)
                    .set(BatchEvent::getUpdatedBy, member.userId())
                    .set(BatchEvent::getUpdatedAt, BeijingTime.now()));
            updateEntryTimeline(batch, entry.getId(), newPen.getId(), entryDate, newCount);
            logRecord("UPDATE", BizType.BATCH_ENTRY.name(), batch, entry.getId(), batch.getBatchNo(),
                    "修改入栏记录为 " + support.countText(batch, newCount));
        }
        return detail(batchId);
    }

    @Override
    @Transactional
    public void delete(Long batchId, int version) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(batchId);
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.stateNotAllowed("该批次已结束，不能删除");
        }
        if (batchMapper.countFollowUpEvents(batchId) > 0) {
            throw HerdErrors.changeRequired("这个批次已有后续记录，不能删除");
        }
        if (batch.getSourceRecordType() != null) {
            throw HerdErrors.stateNotAllowed("这个批次由产仔或出雏转入，请在繁育记录中处理");
        }
        if (version != (batch.getVersion() == null ? 0 : batch.getVersion())) {
            throw HerdErrors.versionConflict();
        }
        for (BatchPen row : support.pensOfBatch(batchId)) {
            if (row.getStockCount() != null && row.getStockCount() > 0) {
                penMapper.addStock(row.getPenId(), -row.getStockCount(), BeijingTime.now());
            }
            batchPenMapper.deleteById(row.getId());
        }
        eventMapper.delete(Wrappers.<BatchEvent>lambdaQuery().eq(BatchEvent::getBatchId, batchId));
        timelineMapper.delete(Wrappers.<BatchTimeline>lambdaQuery().eq(BatchTimeline::getBatchId, batchId));
        batchMapper.deleteById(batchId);
        logRecord("DELETE", BizType.BATCH.name(), batch, batchId, batch.getBatchNo(), "删除批次");
    }

    @Override
    @Transactional
    public TransferResultVO createTransfer(TransferCreateRequest request, String requestKey) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(request.getBatchId());
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        if (request.getFromPenId() == null || request.getToPenId() == null) {
            throw HerdErrors.fieldInvalid("to_pen_id", "请选择转入栏舍");
        }
        if (request.getFromPenId().equals(request.getToPenId())) {
            throw HerdErrors.fieldInvalid("to_pen_id", "转入栏舍不能与转出栏舍相同");
        }
        int quantity = requireCount(request.getQuantity());
        Pen fromPen = requirePenInFarm(member.farmId(), request.getFromPenId(), "from_pen_id", "请选择栏舍");
        Pen toPen = requirePenInFarm(member.farmId(), request.getToPenId(), "to_pen_id", "请选择转入栏舍");
        if (!"ENABLED".equals(toPen.getStatus())) {
            throw HerdErrors.penDisabled("这个栏舍已停用，不能转入");
        }
        LocalDate eventDate = requireEventDate(request.getEventDate(), batch, fromPen.getId(), "转栏");
        validateRemark(request.getRemark(), 100, "remark");
        ensurePenCategory(toPen, batch.getCategoryId(), batch.getId());
        BatchPen fromRow = findBatchPen(batch.getId(), fromPen.getId());
        int fromStock = fromRow == null ? 0 : fromRow.getStockCount();
        if (fromStock < quantity) {
            throw HerdErrors.countExceeded(fromPen.getId(), fromPen.getPenName(), fromStock, request.getSeenStockCount());
        }
        int toBefore = toPen.getStockCount() == null ? 0 : toPen.getStockCount();
        int after = toBefore + quantity;
        int over = Math.max(0, after - capacityOf(toPen));
        if (over > 0 && !Boolean.TRUE.equals(request.getCapacityConfirmed())) {
            throw HerdErrors.capacityConfirm(toPen.getId(), toPen.getPenName(), "转入", capacityOf(toPen), toBefore, after,
                    over);
        }
        if (batchPenMapper.deduct(batch.getId(), fromPen.getId(), quantity, eventDate, BeijingTime.now()) == 0) {
            int latest = latestStock(batch.getId(), fromPen.getId());
            throw HerdErrors.countExceeded(fromPen.getId(), fromPen.getPenName(), latest, request.getSeenStockCount());
        }
        if (batchPenMapper.addStock(batch.getId(), toPen.getId(), quantity, eventDate, BeijingTime.now()) == 0) {
            insertBatchPen(batch, toPen.getId(), quantity, eventDate);
        }
        penMapper.addStock(fromPen.getId(), -quantity, BeijingTime.now());
        penMapper.addStock(toPen.getId(), quantity, BeijingTime.now());
        bumpBatchVersion(batch);

        BatchEvent event = new BatchEvent();
        event.setFarmId(member.farmId());
        event.setBatchId(batch.getId());
        event.setEventType("TRANSFER");
        event.setEventDate(eventDate);
        event.setPenId(fromPen.getId());
        event.setToPenId(toPen.getId());
        event.setQuantity(quantity);
        event.setRemark(request.getRemark());
        event.setExecutorUserId(member.userId());
        event.setCapacityConfirmed(over > 0);
        event.setOverCapacityCount(over);
        event.setStatus("EFFECTIVE");
        event.setClientRequestId(requestKey);
        event.setVersion(0);
        event.setDeleted(false);
        eventMapper.insert(event);
        writeTimeline(batch, fromPen.getId(), "TRANSFER", BizType.BATCH_TRANSFER.name(), event.getId(), eventDate,
                "转栏 · " + support.countText(batch, quantity), fromPen.getPenName() + " → " + toPen.getPenName() + " · "
                        + support.userName(member.userId()),
                detail("登记人", support.userName(member.userId()), "登记时间", BeijingTime.format(eventDate)),
                member.userId());
        return transferResult(batch, event, fromPen.getPenName(), toPen.getPenName(),
                fromStock - quantity, toBefore + quantity);
    }

    @Override
    @Transactional
    public TransferResultVO updateTransfer(Long eventId, TransferUpdateRequest request) {
        FarmContext.FarmMember member = support.context();
        BatchEvent event = requireEvent(eventId, "TRANSFER");
        Batch batch = access.require(event.getBatchId());
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        if (request.getVersion() == null || !request.getVersion().equals(event.getVersion())) {
            throw HerdErrors.versionConflict();
        }
        if (request.getFromPenId() == null || request.getToPenId() == null
                || request.getFromPenId().equals(request.getToPenId())) {
            throw HerdErrors.fieldInvalid("to_pen_id", "请选择转入栏舍");
        }
        int quantity = requireCount(request.getQuantity());
        Pen fromPen = requirePenInFarm(member.farmId(), request.getFromPenId(), "from_pen_id", "请选择栏舍");
        Pen toPen = requirePenInFarm(member.farmId(), request.getToPenId(), "to_pen_id", "请选择转入栏舍");
        if (!"ENABLED".equals(toPen.getStatus())) {
            throw HerdErrors.penDisabled("这个栏舍已停用，不能转入");
        }
        LocalDate eventDate = requireEventDate(request.getEventDate(), batch, fromPen.getId(), "转栏");
        validateRemark(request.getRemark(), 100, "remark");
        ensurePenCategory(toPen, batch.getCategoryId(), batch.getId());

        // 先撤销原记录：转出栏舍加回、转入栏舍扣回
        BatchPen oldFrom = findBatchPen(batch.getId(), event.getPenId());
        BatchPen oldTo = findBatchPen(batch.getId(), event.getToPenId());
        if (oldTo != null && oldTo.getStockCount() < event.getQuantity()) {
            throw HerdErrors.countExceeded(oldTo.getPenId(), penName(oldTo.getPenId()),
                    oldTo.getStockCount() == null ? 0 : oldTo.getStockCount(), null);
        }
        if (batchPenMapper.addStock(batch.getId(), event.getPenId(), event.getQuantity(), event.getEventDate(),
                BeijingTime.now()) == 0) {
            insertBatchPen(batch, event.getPenId(), event.getQuantity(), event.getEventDate());
        }
        if (event.getToPenId() != null) {
            batchPenMapper.deduct(batch.getId(), event.getToPenId(), event.getQuantity(), event.getEventDate(),
                    BeijingTime.now());
        }
        penMapper.addStock(event.getPenId(), event.getQuantity(), BeijingTime.now());
        if (event.getToPenId() != null) {
            penMapper.addStock(event.getToPenId(), -event.getQuantity(), BeijingTime.now());
        }
        // 再按新值执行
        BatchPen newFrom = findBatchPen(batch.getId(), fromPen.getId());
        int newFromStock = newFrom == null ? 0 : newFrom.getStockCount();
        if (newFromStock < quantity) {
            throw HerdErrors.countExceeded(fromPen.getId(), fromPen.getPenName(), newFromStock, null);
        }
        if (batchPenMapper.deduct(batch.getId(), fromPen.getId(), quantity, eventDate, BeijingTime.now()) == 0) {
            throw HerdErrors.countExceeded(fromPen.getId(), fromPen.getPenName(),
                    latestStock(batch.getId(), fromPen.getId()), null);
        }
        if (batchPenMapper.addStock(batch.getId(), toPen.getId(), quantity, eventDate, BeijingTime.now()) == 0) {
            insertBatchPen(batch, toPen.getId(), quantity, eventDate);
        }
        penMapper.addStock(fromPen.getId(), -quantity, BeijingTime.now());
        penMapper.addStock(toPen.getId(), quantity, BeijingTime.now());
        int toBefore = (toPen.getStockCount() == null ? 0 : toPen.getStockCount())
                - (oldTo != null && toPen.getId().equals(oldTo.getPenId()) ? oldTo.getStockCount() : 0);
        int over = Math.max(0, toBefore + quantity - capacityOf(toPen));
        eventMapper.update(null, Wrappers.<BatchEvent>lambdaUpdate()
                .eq(BatchEvent::getId, eventId)
                .eq(BatchEvent::getVersion, request.getVersion())
                .set(BatchEvent::getPenId, fromPen.getId())
                .set(BatchEvent::getToPenId, toPen.getId())
                .set(BatchEvent::getQuantity, quantity)
                .set(BatchEvent::getEventDate, eventDate)
                .set(BatchEvent::getRemark, request.getRemark())
                .set(BatchEvent::getCapacityConfirmed, Boolean.TRUE.equals(request.getCapacityConfirmed()))
                .set(BatchEvent::getOverCapacityCount, over)
                .set(BatchEvent::getVersion, request.getVersion() + 1)
                .set(BatchEvent::getUpdatedBy, member.userId())
                .set(BatchEvent::getUpdatedAt, BeijingTime.now()));
        bumpBatchVersion(batch);
        updateTimeline(batch.getId(), BizType.BATCH_TRANSFER.name(), eventId, fromPen.getId(), eventDate,
                "转栏 · " + support.countText(batch, quantity), fromPen.getPenName() + " → " + toPen.getPenName() + " · "
                        + support.userName(member.userId()));
        logRecord("UPDATE", BizType.BATCH_TRANSFER.name(), batch, eventId, batch.getBatchNo(),
                "修改转栏记录为 " + support.countText(batch, quantity));
        return transferResult(batch, event, fromPen.getPenName(), toPen.getPenName(),
                newFromStock - quantity, toBefore + quantity);
    }

    @Override
    @Transactional
    public DeathResultVO createDeath(DeathCreateRequest request, String requestKey) {
        FarmContext.FarmMember member = support.context();
        Batch batch = access.require(request.getBatchId());
        if (!"ACTIVE".equals(batch.getStatus())) {
            throw HerdErrors.batchEnded();
        }
        int quantity = requireCount(request.getQuantity());
        List<BatchPen> rows = support.positivePensOfBatch(batch.getId());
        Long penId = request.getPenId();
        if (penId == null) {
            if (rows.size() == 1) {
                penId = rows.get(0).getPenId();
            } else {
                throw HerdErrors.fieldInvalid("pen_id", "请选择栏舍");
            }
        }
        BatchPen row = findBatchPen(batch.getId(), penId);
        if (row == null || row.getStockCount() == null || row.getStockCount() <= 0) {
            throw HerdErrors.countExceeded(penId, penName(penId), 0, request.getSeenStockCount());
        }
        if (row.getStockCount() < quantity) {
            throw HerdErrors.countExceeded(penId, penName(penId), row.getStockCount(), request.getSeenStockCount());
        }
        access.requireOperatePen(penId);
        String reason = requireReason(request.getReasonCode(), request.getRemark());
        LocalDate eventDate = requireEventDate(request.getEventDate(), batch, penId, "死淘");
        validateRemark(request.getRemark(), 200, "remark");
        List<Long> fileIds = validatePhotos(request.getFileIds());
        if (!fileIds.isEmpty()) {
            bizMediaService.validateForBind(fileIds, FileScene.DEATH, member.userId(), "file_ids");
        }
        Long executorId = resolveExecutor(member, penId, request.getExecutorUserId());
        if (batchPenMapper.deduct(batch.getId(), penId, quantity, eventDate, BeijingTime.now()) == 0) {
            throw HerdErrors.countExceeded(penId, penName(penId), latestStock(batch.getId(), penId),
                    request.getSeenStockCount());
        }
        penMapper.addStock(penId, -quantity, BeijingTime.now());
        int newCurrent = (batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()) - quantity;
        int newDeath = (batch.getDeathCount() == null ? 0 : batch.getDeathCount()) + quantity;
        updateBatchCounters(batch, newCurrent, newDeath, eventDate);

        BatchEvent event = new BatchEvent();
        event.setFarmId(member.farmId());
        event.setBatchId(batch.getId());
        event.setEventType("DEATH");
        event.setEventDate(eventDate);
        event.setPenId(penId);
        event.setQuantity(quantity);
        event.setReasonCode(reason);
        event.setRemark(request.getRemark());
        event.setExecutorUserId(executorId);
        event.setStatus("EFFECTIVE");
        event.setClientRequestId(requestKey);
        event.setVersion(0);
        event.setDeleted(false);
        eventMapper.insert(event);
        if (!fileIds.isEmpty()) {
            bizMediaService.replace(BizType.BATCH_DEATH, event.getId(), member.farmId(), fileIds);
        }
        writeTimeline(batch, penId, "DEATH", BizType.BATCH_DEATH.name(), event.getId(), eventDate,
                deathTitle(batch, quantity, reason), penName(penId) + " · " + executionText(executorId, member.userId()),
                detail("登记人", support.userName(member.userId()), "实际执行人", support.userName(executorId),
                        "登记时间", BeijingTime.format(eventDate), "说明", request.getRemark()),
                member.userId());
        return deathResult(batch, event, quantity);
    }

    @Override
    @Transactional
    public DeathResultVO updateDeath(Long eventId, DeathUpdateRequest request) {
        FarmContext.FarmMember member = support.context();
        BatchEvent event = requireEvent(eventId, "DEATH");
        Batch batch = access.require(event.getBatchId());
        if (request.getVersion() == null || !request.getVersion().equals(event.getVersion())) {
            throw HerdErrors.versionConflict();
        }
        if (member.isStaff()) {
            boolean ownToday = member.userId().equals(event.getCreatedBy())
                    && BeijingTime.today().equals(event.getEventDate());
            if (!ownToday) {
                throw HerdErrors.roleForbidden("超过当天的记录由所有者或场长修改");
            }
            access.requireOperatePen(event.getPenId());
        }
        int quantity = requireCount(request.getQuantity());
        Long penId = request.getPenId() == null ? event.getPenId() : request.getPenId();
        String reason = requireReason(request.getReasonCode(), request.getRemark());
        LocalDate eventDate = requireEventDate(request.getEventDate(), batch, penId, "死淘");
        validateRemark(request.getRemark(), 200, "remark");
        List<Long> fileIds = validatePhotos(request.getFileIds());
        if (!fileIds.isEmpty()) {
            bizMediaService.validateForBind(fileIds, FileScene.DEATH, member.userId(), "file_ids");
        }
        Long executorId = resolveExecutor(member, penId, request.getExecutorUserId());
        access.requireOperatePen(penId);

        // 撤销原扣减
        if (batchPenMapper.addStock(batch.getId(), event.getPenId(), event.getQuantity(), event.getEventDate(),
                BeijingTime.now()) == 0) {
            insertBatchPen(batch, event.getPenId(), event.getQuantity(), event.getEventDate());
        }
        penMapper.addStock(event.getPenId(), event.getQuantity(), BeijingTime.now());
        int restoredCurrent = (batch.getCurrentCount() == null ? 0 : batch.getCurrentCount()) + event.getQuantity();
        int restoredDeath = Math.max(0, (batch.getDeathCount() == null ? 0 : batch.getDeathCount()) - event.getQuantity());
        // 再按新值扣减
        BatchPen row = findBatchPen(batch.getId(), penId);
        int stock = row == null ? 0 : row.getStockCount();
        if (stock < quantity) {
            throw HerdErrors.countExceeded(penId, penName(penId), stock, null);
        }
        if (batchPenMapper.deduct(batch.getId(), penId, quantity, eventDate, BeijingTime.now()) == 0) {
            throw HerdErrors.countExceeded(penId, penName(penId), latestStock(batch.getId(), penId), null);
        }
        penMapper.addStock(penId, -quantity, BeijingTime.now());
        int newCurrent = restoredCurrent - quantity;
        int newDeath = restoredDeath + quantity;
        updateBatchCounters(batch, newCurrent, newDeath, eventDate);
        eventMapper.update(null, Wrappers.<BatchEvent>lambdaUpdate()
                .eq(BatchEvent::getId, eventId)
                .eq(BatchEvent::getVersion, request.getVersion())
                .set(BatchEvent::getPenId, penId)
                .set(BatchEvent::getQuantity, quantity)
                .set(BatchEvent::getReasonCode, reason)
                .set(BatchEvent::getEventDate, eventDate)
                .set(BatchEvent::getRemark, request.getRemark())
                .set(BatchEvent::getExecutorUserId, executorId)
                .set(BatchEvent::getVersion, request.getVersion() + 1)
                .set(BatchEvent::getUpdatedBy, member.userId())
                .set(BatchEvent::getUpdatedAt, BeijingTime.now()));
        bizMediaService.replace(BizType.BATCH_DEATH, eventId, member.farmId(), fileIds);
        updateTimeline(batch.getId(), BizType.BATCH_DEATH.name(), eventId, penId, eventDate,
                deathTitle(batch, quantity, reason), penName(penId) + " · " + executionText(executorId, member.userId()));
        logRecord("UPDATE", BizType.BATCH_DEATH.name(), batch, eventId, batch.getBatchNo(),
                "修改死淘记录为 " + support.countText(batch, quantity));
        return deathResult(batch, event, quantity);
    }

    private BatchEvent requireEvent(Long eventId, String type) {
        if (eventId == null) {
            throw HerdErrors.notFound();
        }
        BatchEvent event = eventMapper.selectById(eventId);
        if (event == null) {
            throw HerdErrors.notFound();
        }
        if (!type.equals(event.getEventType())) {
            throw HerdErrors.stateNotAllowed("记录类型不允许该操作");
        }
        return event;
    }

    private Pen requirePenInFarm(Long farmId, Long penId, String field, String message) {
        if (penId == null) {
            throw HerdErrors.fieldInvalid(field, message);
        }
        return support.pensOfFarm(farmId).stream().filter(pen -> penId.equals(pen.getId())).findFirst()
                .orElseThrow(HerdErrors::notFound);
    }

    private void ensurePenCategory(Pen pen, Long categoryId, Long excludeBatchId) {
        for (Batch other : support.activeBatches(pen.getFarmId())) {
            if (other.getId().equals(excludeBatchId) || other.getCategoryId().equals(categoryId)) {
                continue;
            }
            boolean inPen = support.positivePensOfBatch(other.getId()).stream()
                    .anyMatch(row -> pen.getId().equals(row.getPenId()));
            if (!inPen) {
                continue;
            }
            throw HerdErrors.penCategoryConflict(pen.getPenName() + "已有其他品类的批次在栏，同一栏舍只能放同一品类");
        }
    }

    private LocalDate requireEventDate(LocalDate eventDate, Batch batch, Long penId, String action) {
        if (eventDate == null) {
            throw HerdErrors.fieldInvalid("event_date", "请选择" + action + "日期");
        }
        if (eventDate.isAfter(BeijingTime.today())) {
            throw HerdErrors.fieldInvalid("event_date", action + "日期不能晚于今天");
        }
        if (batch.getEntryDate() != null && eventDate.isBefore(batch.getEntryDate())) {
            throw HerdErrors.fieldInvalid("event_date", action + "日期不能早于入栏日期 "
                    + BeijingTime.format(batch.getEntryDate()));
        }
        BatchPen row = findBatchPen(batch.getId(), penId);
        if (row != null && row.getFirstInDate() != null && eventDate.isBefore(row.getFirstInDate())) {
            throw HerdErrors.fieldInvalid("event_date", action + "日期不能早于进入该栏舍的日期 "
                    + BeijingTime.format(row.getFirstInDate()));
        }
        return eventDate;
    }

    private static int requireEntryAge(Integer age) {
        if (age == null) {
            throw HerdErrors.fieldInvalid("entry_age_days", "请填写入栏日龄");
        }
        if (age < 0 || age > 3650) {
            throw HerdErrors.fieldInvalid("entry_age_days", "入栏日龄需为 0 或正整数");
        }
        return age;
    }

    private static int requireCount(Integer count) {
        return requireCount(count, "quantity");
    }

    private static int requireCount(Integer count, String field) {
        if (count == null || count <= 0) {
            throw HerdErrors.fieldInvalid(field, "数量需为大于 0 的整数");
        }
        return count;
    }

    private static BigDecimal validateWeight(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        if (weight.signum() <= 0 || weight.compareTo(new BigDecimal("9999.99")) > 0 || weight.scale() > 2) {
            throw HerdErrors.fieldInvalid("entry_avg_weight", "请输入大于 0 的数字，最多保留 2 位小数");
        }
        return weight;
    }

    private static BigDecimal validatePrice(BigDecimal price, String source) {
        if ("PURCHASED".equals(source) && price == null) {
            throw HerdErrors.fieldInvalid("intro_unit_price", "请填写引种单价");
        }
        if (price == null) {
            return null;
        }
        if (price.signum() < 0 || price.compareTo(new BigDecimal("999999.99")) > 0 || price.scale() > 2) {
            throw HerdErrors.fieldInvalid("intro_unit_price", "引种单价最多保留 2 位小数");
        }
        return price;
    }

    private String requireReason(String code, String remark) {
        String reason = upper(code);
        if (reason == null || !List.of("SICK", "CULL", "ACCIDENT", "OTHER").contains(reason)) {
            throw HerdErrors.fieldInvalid("reason_code", "请选择死淘原因");
        }
        if ("OTHER".equals(reason) && (remark == null || remark.isBlank())) {
            throw HerdErrors.fieldInvalid("remark", "选择“其他”时请写明原因");
        }
        return reason;
    }

    private static void validateRemark(String remark, int max, String field) {
        if (remark != null && remark.codePointCount(0, remark.length()) > max) {
            throw HerdErrors.fieldInvalid(field, "文字不能超过 " + max + " 个字");
        }
    }

    private static List<Long> validatePhotos(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        if (fileIds.size() > 6) {
            throw HerdErrors.fieldInvalid("file_ids", "最多添加 6 张照片");
        }
        return fileIds;
    }

    private Long resolveExecutor(FarmContext.FarmMember member, Long penId, Long requested) {
        if (member.isStaff()) {
            return member.userId();
        }
        if (requested == null || requested.equals(member.userId())) {
            return member.userId();
        }
        boolean assigned = farmLookup.pensOfFarm(member.farmId()).stream()
                .anyMatch(row -> row.getUserId().equals(requested) && row.getPenId().equals(penId));
        if (!assigned) {
            throw HerdErrors.fieldInvalid("executor_user_id", "请选择实际执行人");
        }
        return requested;
    }

    private void updateBatchCounters(Batch batch, int current, int death, LocalDate eventDate) {
        boolean ended = current <= 0;
        int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batch.getId())
                .eq(Batch::getVersion, batch.getVersion())
                .set(Batch::getCurrentCount, Math.max(0, current))
                .set(Batch::getDeathCount, Math.max(0, death))
                .set(Batch::getStatus, ended ? "ENDED" : "ACTIVE")
                .set(Batch::getEndType, ended ? "ALL_DEAD" : null)
                .set(Batch::getEndDate, ended ? eventDate : null)
                .set(Batch::getEndedAt, ended ? BeijingTime.now() : null)
                .set(Batch::getVersion, batch.getVersion() + 1)
                .set(Batch::getUpdatedAt, BeijingTime.now()));
        if (rows == 0) {
            throw HerdErrors.versionConflict();
        }
        batch.setCurrentCount(Math.max(0, current));
        batch.setDeathCount(Math.max(0, death));
        batch.setStatus(ended ? "ENDED" : "ACTIVE");
        batch.setVersion(batch.getVersion() + 1);
        if (ended) {
            batch.setEndType("ALL_DEAD");
            batch.setEndDate(eventDate);
        } else {
            batch.setEndType(null);
            batch.setEndDate(null);
        }
    }

    private void bumpBatchVersion(Batch batch) {
        int rows = batchMapper.update(null, Wrappers.<Batch>lambdaUpdate()
                .eq(Batch::getId, batch.getId())
                .eq(Batch::getVersion, batch.getVersion())
                .set(Batch::getVersion, batch.getVersion() + 1)
                .set(Batch::getUpdatedAt, BeijingTime.now()));
        if (rows == 0) {
            throw HerdErrors.versionConflict();
        }
        batch.setVersion(batch.getVersion() + 1);
    }

    private BatchPen findBatchPen(Long batchId, Long penId) {
        if (penId == null) {
            return null;
        }
        List<BatchPen> rows = batchPenMapper.selectList(Wrappers.<BatchPen>lambdaQuery()
                .eq(BatchPen::getBatchId, batchId)
                .eq(BatchPen::getPenId, penId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int latestStock(Long batchId, Long penId) {
        BatchPen row = findBatchPen(batchId, penId);
        return row == null || row.getStockCount() == null ? 0 : row.getStockCount();
    }

    private void insertBatchPen(Batch batch, Long penId, int quantity, LocalDate eventDate) {
        BatchPen row = new BatchPen();
        row.setFarmId(batch.getFarmId());
        row.setBatchId(batch.getId());
        row.setPenId(penId);
        row.setStockCount(quantity);
        row.setFirstInDate(eventDate);
        batchPenMapper.insert(row);
    }

    private String penName(Long penId) {
        return support.pensByIds(List.of(penId)).stream().findFirst().map(Pen::getPenName).orElse("栏舍");
    }

    private static int capacityOf(Pen pen) {
        return pen.getCapacity() == null ? 0 : pen.getCapacity();
    }

    private TransferResultVO transferResult(Batch batch, BatchEvent event, String fromName, String toName,
                                            int fromStock, int toStock) {
        TransferResultVO vo = new TransferResultVO();
        vo.setEventId(event.getId());
        vo.setBatchId(batch.getId());
        vo.setQuantity(event.getQuantity());
        vo.setOverCapacityCount(event.getOverCapacityCount());
        BatchPenItem from = new BatchPenItem();
        from.setPenId(event.getPenId());
        from.setPenName(fromName);
        from.setStockCount(fromStock);
        BatchPenItem to = new BatchPenItem();
        to.setPenId(event.getToPenId());
        to.setPenName(toName);
        to.setStockCount(toStock);
        vo.setFromPen(from);
        vo.setToPen(to);
        vo.setMessage("已转栏 " + support.countText(batch, event.getQuantity()) + "到 " + toName);
        return vo;
    }

    private DeathResultVO deathResult(Batch batch, BatchEvent event, int quantity) {
        DeathResultVO vo = new DeathResultVO();
        vo.setEventId(event.getId());
        vo.setBatchId(batch.getId());
        vo.setQuantity(quantity);
        vo.setCurrentCount(batch.getCurrentCount());
        vo.setDeathCount(batch.getDeathCount());
        vo.setMortalityRate(HerdSupport.mortality(batch.getDeathCount(), batch.getEntryCount()));
        vo.setBatchStatus(batch.getStatus());
        vo.setMessage("已登记死淘 " + support.countText(batch, quantity) + "，存栏已更新");
        vo.setBatchEnded("ENDED".equals(batch.getStatus()));
        return vo;
    }

    private void writeTimeline(Batch batch, Long penId, String recordType, String bizType, Long bizId,
                               LocalDate bizDate, String title, String subtitle, List<Map<String, String>> detail,
                               Long operatorUserId) {
        BatchTimeline row = new BatchTimeline();
        row.setFarmId(batch.getFarmId());
        row.setBatchId(batch.getId());
        row.setPenId(penId);
        row.setRecordType(recordType);
        row.setBizType(bizType);
        row.setBizId(bizId);
        row.setBizDate(bizDate);
        row.setOccurredAt(BeijingTime.now());
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.setDetailJson(toJson(detail));
        row.setOperatorUserId(operatorUserId);
        row.setDeleted(false);
        timelineMapper.insert(row);
    }

    private void updateTimeline(Long batchId, String bizType, Long bizId, Long penId, LocalDate bizDate,
                                String title, String subtitle) {
        timelineMapper.update(null, Wrappers.<BatchTimeline>lambdaUpdate()
                .eq(BatchTimeline::getBatchId, batchId)
                .eq(BatchTimeline::getBizType, bizType)
                .eq(BatchTimeline::getBizId, bizId)
                .set(BatchTimeline::getPenId, penId)
                .set(BatchTimeline::getBizDate, bizDate)
                .set(BatchTimeline::getTitle, title)
                .set(BatchTimeline::getSubtitle, subtitle)
                .set(BatchTimeline::getUpdatedAt, BeijingTime.now()));
    }

    private void updateEntryTimeline(Batch batch, Long eventId, Long penId, LocalDate entryDate, int count) {
        timelineMapper.update(null, Wrappers.<BatchTimeline>lambdaUpdate()
                .eq(BatchTimeline::getBatchId, batch.getId())
                .eq(BatchTimeline::getBizType, BizType.BATCH_ENTRY.name())
                .eq(BatchTimeline::getBizId, eventId)
                .set(BatchTimeline::getPenId, penId)
                .set(BatchTimeline::getBizDate, entryDate)
                .set(BatchTimeline::getTitle, "入栏 " + support.countText(batch, count))
                .set(BatchTimeline::getUpdatedAt, BeijingTime.now()));
    }

    private static String toJson(List<Map<String, String>> detail) {
        try {
            return JSON.writeValueAsString(detail);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<Map<String, String>> detail(String... values) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("key", values[i]);
            item.put("value", values[i + 1]);
            result.add(item);
        }
        return result;
    }

    private String deathTitle(Batch batch, int quantity, String reason) {
        return "死淘 · " + support.countText(batch, quantity) + " · " + switch (reason) {
            case "SICK" -> "病死";
            case "CULL" -> "淘汰";
            case "ACCIDENT" -> "意外";
            default -> "其他";
        };
    }

    private String executionText(Long executorId, Long registrantId) {
        if (executorId == null || executorId.equals(registrantId)) {
            return support.userName(registrantId);
        }
        return "实际执行人 " + support.userName(executorId);
    }

    private static String sourceText(String source) {
        return "SELF_BRED".equals(source) ? "自繁" : "外购";
    }

    private void logRecord(String action, String bizType, Batch batch, Long bizId, String targetName, String extra) {
        operationLogRecorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .opType("RECORD_EDIT")
                .opAction(action)
                .bizType(bizType)
                .bizId(bizId)
                .targetName(targetName)
                .farmId(batch.getFarmId())
                .searchText(batch.getBatchNo())
                .extraText(extra)
                .build());
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private Object resultOf(BatchEvent event, Batch batch) {
        Long farmId = batch.getFarmId();
        String unit = support.farmUnit(farmId);
        if ("ENTRY".equals(event.getEventType())) {
            Pen pen = support.pensByIds(List.of(event.getPenId())).stream().findFirst().orElse(null);
            BatchEntryResultVO vo = new BatchEntryResultVO();
            vo.setBatchId(batch.getId());
            vo.setPenId(event.getPenId());
            vo.setBatchNo(batch.getBatchNo());
            vo.setQrContent(qrLinks.content(batch.getQrToken()));
            vo.setFarmName(farmNameOf(farmId));
            vo.setPenName(pen == null ? null : pen.getPenName());
            vo.setBreedName(support.breedName(batch.getBreedId()));
            vo.setPurpose(batch.getPurpose());
            vo.setEntryDate(BeijingTime.format(batch.getEntryDate()));
            vo.setEntryCount(event.getQuantity());
            vo.setEntryAgeDays(batch.getEntryAgeDays());
            vo.setPenStockAfter(pen == null ? null : pen.getStockCount());
            vo.setCapacity(pen == null ? null : pen.getCapacity());
            vo.setOverCapacityCount(event.getOverCapacityCount());
            vo.setUnitText(support.unitText(unit));
            vo.setLabelText(vo.getFarmName() + " · " + vo.getPenName() + " · " + vo.getBreedName());
            return vo;
        }
        if ("TRANSFER".equals(event.getEventType())) {
            TransferResultVO vo = new TransferResultVO();
            vo.setEventId(event.getId());
            vo.setBatchId(batch.getId());
            vo.setQuantity(event.getQuantity());
            vo.setOverCapacityCount(event.getOverCapacityCount());
            vo.setFromPen(penItem(event.getPenId(), null));
            vo.setToPen(penItem(event.getToPenId(), null));
            vo.setMessage("已转栏 " + support.countText(batch, event.getQuantity()) + "到 "
                    + (vo.getToPen() == null ? "转入栏舍" : vo.getToPen().getPenName()));
            return vo;
        }
        DeathResultVO vo = new DeathResultVO();
        vo.setEventId(event.getId());
        vo.setBatchId(batch.getId());
        vo.setQuantity(event.getQuantity());
        int current = batch.getCurrentCount() == null ? 0 : batch.getCurrentCount();
        int death = batch.getDeathCount() == null ? 0 : batch.getDeathCount();
        vo.setCurrentCount(current);
        vo.setDeathCount(death);
        vo.setMortalityRate(HerdSupport.mortality(death, batch.getEntryCount()));
        vo.setBatchStatus(batch.getStatus());
        vo.setMessage("已登记死淘 " + support.countText(batch, event.getQuantity()) + "，存栏已更新");
        vo.setBatchEnded("ENDED".equals(batch.getStatus()));
        return vo;
    }

    private void applyTimelineAction(TimelineItemVO item, Batch batch, BatchTimeline row,
                                     FarmContext.FarmMember member, boolean manager, boolean hasFollowUp) {
        boolean stockRecord = "ENTRY".equals(row.getRecordType()) || "TRANSFER".equals(row.getRecordType())
                || "DEATH".equals(row.getRecordType()) || "SALE".equals(row.getRecordType());
        if (!stockRecord) {
            item.setAction(TimelineItemVO.ACTION_NONE);
            return;
        }
        boolean active = "ACTIVE".equals(batch.getStatus());
        boolean death = "DEATH".equals(row.getRecordType());
        boolean transfer = "TRANSFER".equals(row.getRecordType());
        boolean entry = "ENTRY".equals(row.getRecordType());
        boolean ownTodayDeath = member.isStaff() && death && Boolean.TRUE.equals(item.getIsToday())
                && member.userId().equals(row.getOperatorUserId());
        if (manager) {
            boolean editable;
            if (entry) {
                editable = active && !hasFollowUp;
            } else if (transfer) {
                editable = active;
            } else {
                editable = death;
            }
            editable = editable && BeijingTime.withinEditWindow(row.getBizDate(), 3);
            item.setAction(editable ? TimelineItemVO.ACTION_EDIT : TimelineItemVO.ACTION_CHANGE_REQUEST);
            if (editable) {
                item.setChangeState(ChangeRequestStateVO.builder().canEdit(true)
                        .editDeadline(BeijingTime.editDeadline(row.getBizDate(), 3)).build());
            }
        } else if (ownTodayDeath) {
            item.setAction(TimelineItemVO.ACTION_EDIT);
        } else {
            item.setAction(TimelineItemVO.ACTION_NONE);
            // 员工只能改本人当天的死淘；入栏、转栏记录员工无可编辑入口，不显示修改说明
            if (death) {
                item.setActionNote("超过当天的记录由所有者或场长修改");
            }
        }
    }

    private ChangeRequestStateVO changeState(BatchEvent event, FarmContext.FarmMember member, Batch batch) {
        boolean manager = HerdSupport.isManager(member);
        boolean entry = "ENTRY".equals(event.getEventType());
        boolean death = "DEATH".equals(event.getEventType());
        boolean editable = manager
                && (entry ? "ACTIVE".equals(batch.getStatus()) && batchMapper.countFollowUpEvents(batch.getId()) == 0
                : death || "ACTIVE".equals(batch.getStatus()))
                && BeijingTime.withinEditWindow(event.getEventDate(), 3);
        boolean ownTodayDeath = member.isStaff() && death
                && BeijingTime.today().equals(event.getEventDate())
                && member.userId().equals(event.getCreatedBy());
        return ChangeRequestStateVO.builder()
                .canEdit(editable || ownTodayDeath)
                .editDeadline(BeijingTime.editDeadline(event.getEventDate(), 3))
                .build();
    }

    private BatchDetailVO.BatchActions actions(Batch batch, FarmContext.FarmMember member) {
        boolean active = "ACTIVE".equals(batch.getStatus());
        boolean hasFollowUp = batchMapper.countFollowUpEvents(batch.getId()) > 0;
        boolean manager = HerdSupport.isManager(member);
        BatchDetailVO.BatchActions actions = new BatchDetailVO.BatchActions();
        actions.setCanTransfer(manager && active);
        actions.setCanRegisterDeath(active);
        actions.setCanSubmitChangeRequest(manager && (active || "ENDED".equals(batch.getStatus())));
        actions.setCanEditEntry(manager && active && !hasFollowUp);
        actions.setCanDelete(manager && active && !hasFollowUp && batch.getSourceRecordType() == null);
        return actions;
    }

    private int visibleBatchCount(Long farmId, String status, FarmContext.FarmMember member) {
        return (int) support.batches(farmId, status).stream()
                .filter(batch -> support.visibleToStaff(batch, member))
                .count();
    }

    private boolean matches(Batch batch, List<BatchPen> rows, Map<Long, Pen> penMap,
                            BatchListQuery query, String keyword) {
        if (query.getPurpose() != null && !query.getPurpose().isBlank()
                && !query.getPurpose().equalsIgnoreCase(batch.getPurpose())) {
            return false;
        }
        if (query.getCategoryId() != null && !query.getCategoryId().equals(batch.getCategoryId())) {
            return false;
        }
        if (query.getSpecies() != null && !query.getSpecies().isBlank()) {
            CategoryInfo category = support.category(batch.getCategoryId());
            if (category == null || !query.getSpecies().equalsIgnoreCase(category.species())) {
                return false;
            }
        }
        if (query.getPenId() != null && rows.stream().noneMatch(row -> query.getPenId().equals(row.getPenId()))) {
            return false;
        }
        if (keyword == null) {
            return true;
        }
        if (contains(batch.getBatchNo(), keyword) || contains(support.breedName(batch.getBreedId()), keyword)
                || contains(support.categoryName(batch.getCategoryId()), keyword)) {
            return true;
        }
        for (BatchPen row : rows) {
            Pen pen = penMap.get(row.getPenId());
            if (pen != null && (contains(pen.getPenName(), keyword) || contains(pen.getPenCode(), keyword))) {
                return true;
            }
        }
        return false;
    }

    private BatchLookupVO lookupResult(String result, String message) {
        BatchLookupVO vo = new BatchLookupVO();
        vo.setResult(result);
        vo.setMessage(message);
        return vo;
    }

    private List<TimelineItemVO.KeyValue> parseDetail(String json) {
        List<TimelineItemVO.KeyValue> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (node != null && node.isArray()) {
                for (JsonNode child : node) {
                    TimelineItemVO.KeyValue kv = new TimelineItemVO.KeyValue();
                    JsonNode keyNode = child.get("key");
                    JsonNode valueNode = child.get("value");
                    kv.setKey(keyNode == null || keyNode.isNull() ? null : keyNode.asString());
                    kv.setValue(valueNode == null || valueNode.isNull() ? null : valueNode.asString());
                    result.add(kv);
                }
            }
        } catch (RuntimeException ignored) {
            // 历史数据 JSON 不合法时不影响时间线展示
        }
        return result;
    }

    private static boolean matchType(String type, String recordType) {
        if (type == null || "ALL".equals(type)) {
            return true;
        }
        if ("STOCK".equals(type)) {
            return "ENTRY".equals(recordType) || "TRANSFER".equals(recordType)
                    || "DEATH".equals(recordType) || "SALE".equals(recordType);
        }
        return type.equals(recordType);
    }

    private UserBriefVO userBrief(Long userId) {
        if (userId == null) {
            return null;
        }
        String role = null;
        FarmMember row = farmLookup.findActiveMember(FarmContext.require().farmId(), userId);
        if (row != null) {
            role = row.getRole();
        }
        AccountBrief brief = support.account(userId);
        if (brief == null) {
            return null;
        }
        return UserBriefVO.builder().userId(userId).name(brief.name()).role(role).roleText(support.roleText(role))
                .build();
    }

    private PenBriefVO penBrief(Long penId) {
        if (penId == null) {
            return null;
        }
        Pen pen = support.pensByIds(List.of(penId)).stream().findFirst().orElse(null);
        if (pen == null) {
            return null;
        }
        return PenBriefVO.builder().penId(pen.getId()).name(pen.getPenName())
                .penTypeText(support.penTypeText(pen.getPenType())).build();
    }

    private BatchPenItem penItem(Long penId, String name) {
        if (penId == null) {
            return null;
        }
        Pen pen = support.pensByIds(List.of(penId)).stream().findFirst().orElse(null);
        BatchPenItem item = new BatchPenItem();
        item.setPenId(penId);
        item.setPenName(pen == null ? name : pen.getPenName());
        item.setStockCount(pen == null ? null : pen.getStockCount());
        return item;
    }

    private static BizType mediaType(String eventType) {
        return switch (eventType) {
            case "TRANSFER" -> BizType.BATCH_TRANSFER;
            case "DEATH" -> BizType.BATCH_DEATH;
            default -> BizType.BATCH_ENTRY;
        };
    }

    private String farmNameOf(Long farmId) {
        FarmContext.FarmMember current = FarmContext.get();
        if (current != null && current.farmId().equals(farmId)) {
            return current.farmName();
        }
        FarmContext.FarmMember loaded = FarmContext.get();
        if (loaded != null && loaded.farmId().equals(farmId)) {
            return loaded.farmName();
        }
        return "";
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean contains(String text, String keyword) {
        return text != null && text.contains(keyword);
    }
}
