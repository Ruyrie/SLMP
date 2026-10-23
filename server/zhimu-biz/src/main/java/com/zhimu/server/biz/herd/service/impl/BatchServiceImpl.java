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
import com.zhimu.server.biz.herd.mapper.BatchTimelineMapper;
import com.zhimu.server.biz.herd.query.BatchListQuery;
import com.zhimu.server.biz.herd.query.BatchLookupQuery;
import com.zhimu.server.biz.herd.query.TimelineQuery;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.support.BatchAccess;
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
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
    private final FarmLookup farmLookup;
    private final FarmMemberCache memberCache;
    private final BizMediaService bizMediaService;
    private final List<BatchTimelineProvider> timelineProviders;

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
        tabs.put("ACTIVE", (int) support.countBatches(farmId, "ACTIVE"));
        tabs.put("ENDED", (int) support.countBatches(farmId, "ENDED"));
        tabs.put("ALL", (int) support.countBatches(farmId, "ALL"));
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
            String token = QrCodes.parseToken(qrContent);
            if (token == null) {
                return lookupResult(BatchLookupVO.NOT_BATCH_QR, "没有识别到批次二维码，请对准批次标签重试");
            }
            batch = TenantSkip.call(() -> batchMapper.selectByQrTokenIncludingDeleted(token));
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
            return lookupResult(BatchLookupVO.NO_PERMISSION,
                    "你没有这个批次的查看权限。这个批次不属于你加入的养殖场，或不在你负责的栏舍内。");
        }
        if (!support.visibleToStaff(batch, target)) {
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
        vo.setQrContent(QrCodes.content(batch.getQrToken()));
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
        // 待接入公共修改申请统计：当前没有本批次的待审核申请数
        vo.setPendingChangeRequestCount(0);
        BatchDetailVO.WithdrawalInfo withdrawal = new BatchDetailVO.WithdrawalInfo();
        // 待模块08接入：用药与休药
        withdrawal.setInWithdrawal(false);
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
        if (timelineProviders != null) {
            for (BatchTimelineProvider provider : timelineProviders) {
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
        vo.setPhotos(bizMediaService.list(mediaType(event.getEventType()), event.getId()));
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

    private Object resultOf(BatchEvent event, Batch batch) {
        Long farmId = batch.getFarmId();
        String unit = support.farmUnit(farmId);
        if ("ENTRY".equals(event.getEventType())) {
            Pen pen = support.pensByIds(List.of(event.getPenId())).stream().findFirst().orElse(null);
            BatchEntryResultVO vo = new BatchEntryResultVO();
            vo.setBatchId(batch.getId());
            vo.setPenId(event.getPenId());
            vo.setBatchNo(batch.getBatchNo());
            vo.setQrContent(QrCodes.content(batch.getQrToken()));
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
            vo.setMessage("已转栏 " + event.getQuantity() + " 头到 "
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
        vo.setMessage("已登记死淘 " + event.getQuantity() + " 头，存栏已更新");
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
        boolean ownTodayDeath = member.isStaff() && death && Boolean.TRUE.equals(item.getIsToday())
                && member.userId().equals(row.getOperatorUserId());
        if (manager) {
            boolean editable = active || death;
            editable = editable && !hasFollowUp
                    && BeijingTime.withinEditWindow(row.getBizDate(), 3);
            item.setAction(editable ? TimelineItemVO.ACTION_EDIT : TimelineItemVO.ACTION_CHANGE_REQUEST);
            if (editable) {
                item.setChangeState(ChangeRequestStateVO.builder().canEdit(true)
                        .editDeadline(BeijingTime.editDeadline(row.getBizDate(), 3)).build());
            }
        } else if (ownTodayDeath) {
            item.setAction(TimelineItemVO.ACTION_EDIT);
        } else {
            item.setAction(TimelineItemVO.ACTION_NONE);
            if (!member.isStaff()) {
                item.setActionNote("超过当天的记录由所有者或场长修改");
            } else if (death || "ENTRY".equals(row.getRecordType()) || "TRANSFER".equals(row.getRecordType())) {
                item.setActionNote("超过当天的记录由所有者或场长修改");
            }
        }
    }

    private ChangeRequestStateVO changeState(BatchEvent event, FarmContext.FarmMember member, Batch batch) {
        boolean manager = HerdSupport.isManager(member);
        boolean editable = manager && BeijingTime.withinEditWindow(event.getEventDate(), 3)
                && ("ACTIVE".equals(batch.getStatus()) || "DEATH".equals(event.getEventType()));
        boolean ownTodayDeath = member.isStaff() && "DEATH".equals(event.getEventType())
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
