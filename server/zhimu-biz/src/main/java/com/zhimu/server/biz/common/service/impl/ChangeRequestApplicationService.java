package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.change.ChangeField;
import com.zhimu.server.biz.common.change.ChangeForm;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestHandlerRegistry;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.change.ChangeValidation;
import com.zhimu.server.biz.common.convert.ChangeRequestConvert;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.biz.common.request.ChangeRequestListQuery;
import com.zhimu.server.biz.common.request.ChangeRequestSubmitRequest;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.biz.common.vo.ChangeRequestFormVO;
import com.zhimu.server.biz.common.vo.ChangeRequestVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeStatus;
import com.zhimu.server.common.enums.ChangeType;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.SourceModule;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * API-U01–U04 修改申请：表单上下文、提交、我的申请、详情。
 * <p>
 * 不挂 @RequireFarm：U01/U02/U04 按原记录/申请所属 farm 判权，忽略当前 X-Farm-Id；U03 用 TenantSkip 聚合本人全部养殖场。
 * 员工调用返回 40303；记录版本不一致 40902；状态不符 40903；同一记录已有待审核 40906。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeRequestApplicationService {

    /** 员工不能提交或查看修改申请（与 App 错误文案一致） */
    public static final String MSG_STAFF_FORBIDDEN = "员工不能提交或查看修改申请，如需修改请联系所有者或场长。";
    public static final String MSG_NEED_REQUEST = "这条记录可以直接修改，不需要提交修改申请";
    public static final String MSG_RECORD_DELETED = "该记录已被删除，无法查看";
    public static final String MSG_ALREADY_PENDING = "这条记录已有一条待审核的修改申请。审核完成前不能再次提交。";
    public static final String MSG_NOT_FOUND = "记录不存在或无权查看";
    public static final String MSG_DETAIL_NOT_FOUND = "这条修改申请不存在或无权查看";
    public static final String MSG_VERSION_CONFLICT = "内容已更新，请刷新后再修改";
    public static final String MSG_ALREADY_HANDLED = "这条申请已由其他管理员处理，列表已刷新";
    public static final String MSG_RESUBMITTED = "这条申请已重新提交过，请在列表中查看最新的申请";

    private static final int REASON_MAX = 200;

    private final ChangeRequestHandlerRegistry handlerRegistry;
    private final ChangeRequestMapper changeRequestMapper;
    private final ChangeRequestNoGenerator noGenerator;
    private final ChangeRequestConvert convert;
    private final FarmMemberCache farmMemberCache;

    // ===== U01 =====

    public ChangeRequestFormVO form(String bizTypeCode, Long bizId) {
        Long userId = UserContext.requireUserId();
        BizType bizType = parseBizType(bizTypeCode);
        if (bizId == null || bizId <= 0) {
            throw CommonErrors.fieldError("biz_id", "记录 ID 不正确");
        }
        ChangeRequestHandler handler = handlerRegistry.require(bizType);
        ChangeRecord record = loadRecord(handler, bizType, bizId);
        requireManager(record.farmId(), userId);
        if (record.deleted() || record.voided()) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_RECORD_DELETED);
        }
        if (!handler.requiresRequest(record)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, MSG_NEED_REQUEST);
        }
        ChangeForm form = handler.describeForm(record);
        ChangeRequest pending = TenantSkip.call(() -> changeRequestMapper.selectPending(bizType.name(), bizId));
        return toForm(record, form, pending == null ? null : pending.getId());
    }

    private ChangeRequestFormVO toForm(ChangeRecord record, ChangeForm form, Long pendingRequestId) {
        List<ChangeRequestFormVO.CardItem> card = new ArrayList<>();
        for (com.zhimu.server.biz.common.change.ChangeCardItem item : form.recordCard()) {
            card.add(ChangeRequestFormVO.CardItem.builder().label(item.label()).value(item.value()).build());
        }
        List<ChangeRequestFormVO.Field> fields = new ArrayList<>();
        Map<String, List<String>> recalcTags = new LinkedHashMap<>();
        Map<String, String> fieldHints = new LinkedHashMap<>();
        for (ChangeField field : form.fields()) {
            fields.add(ChangeRequestFormVO.Field.builder()
                    .fieldCode(field.fieldCode())
                    .fieldName(field.fieldName())
                    .value(field.value())
                    .valueUnit(field.valueUnit())
                    .inputType(field.inputType())
                    .options(new ArrayList<>(field.options()))
                    .min(field.min())
                    .max(field.max())
                    .build());
            if (!field.recalcTags().isEmpty()) {
                recalcTags.put(field.fieldCode(), new ArrayList<>(field.recalcTags()));
            }
            String hint = field.hint();
            if ((hint == null || hint.isBlank()) && ChangeField.SELECT.equals(field.inputType())
                    && !field.options().isEmpty()) {
                hint = "可选：" + field.options().stream()
                        .map(option -> option.getLabel() == null ? option.getValue() : option.getLabel())
                        .filter(Objects::nonNull)
                        .reduce((a, b) -> a + "、" + b)
                        .orElse("");
            }
            if (hint != null && !hint.isBlank()) {
                fieldHints.put(field.fieldCode(), hint);
            }
        }
        return ChangeRequestFormVO.builder()
                .bizType(record.bizType().name())
                .bizId(record.bizId())
                .bizTypeText(ChangeRequestConvert.bizTypeText(record.bizType().name()))
                .recordLocator(record.recordLocator())
                .recordTitle(record.recordTitle())
                .recordSubtitle(record.recordSubtitle())
                .recordVersion(record.recordVersion())
                .recordCard(card)
                .fields(fields)
                .linkedRules(new ArrayList<>(form.linkedRules()))
                .reviewTip(form.reviewTip())
                .pendingRequestId(pendingRequestId)
                .voidAllowed(form.voidAllowed())
                .voidNotice(form.voidNotice())
                .voidRecalcTags(new ArrayList<>(form.voidRecalcTags()))
                .recalcTags(recalcTags)
                .fieldHints(fieldHints)
                .build();
    }

    // ===== U02 =====

    @Transactional
    public ChangeRequestVO submit(ChangeRequestSubmitRequest request) {
        Long userId = UserContext.requireUserId();
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求内容为空");
        }
        BizType bizType = parseBizTypeForSubmit(request.getBizType());
        if (request.getBizId() == null || request.getBizId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "记录 ID 不正确");
        }
        ChangeType changeType = ChangeRequestJson.parseChangeType(request.getChangeType());
        if (changeType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "修改类型不正确");
        }
        String reason = trim(request.getReason());
        if (reason.isEmpty()) {
            throw CommonErrors.fieldError("reason", "请填写修改原因");
        }
        if (reason.length() > REASON_MAX) {
            throw CommonErrors.fieldError("reason", "修改原因不能超过 200 字");
        }
        if (request.getRecordVersion() == null || request.getRecordVersion().isBlank()) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }
        if (changeType == ChangeType.UPDATE) {
            if (request.getFieldCode() == null || request.getFieldCode().isBlank()) {
                throw CommonErrors.fieldError("field_code", "请选择要修改的项目");
            }
            if (request.getNewValue() == null || request.getNewValue().isBlank()) {
                throw CommonErrors.fieldError("new_value", "请填写新值");
            }
        }

        ChangeRequestHandler handler = handlerRegistry.require(bizType);
        ChangeRecord record = loadRecord(handler, bizType, request.getBizId());
        FarmContext.FarmMember member = requireManager(record.farmId(), userId);
        if (record.deleted() || record.voided()) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_RECORD_DELETED);
        }
        if (!handler.requiresRequest(record)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, MSG_NEED_REQUEST);
        }
        if (!request.getRecordVersion().equals(record.recordVersion())) {
            throw new BizException(ErrorCode.VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }
        ChangeForm form = handler.describeForm(record);
        if (changeType == ChangeType.DELETE && !form.deleteAllowed()) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "这条记录暂不能删除");
        }
        if (changeType == ChangeType.VOID && !form.voidAllowed()) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "这条记录暂不能作废");
        }
        checkResubmit(request.getResubmitOfId(), userId, bizType, request.getBizId());
        checkPending(bizType, request.getBizId());

        ChangeValidation validation = handler.validateChange(record, form, changeType,
                trim(request.getFieldCode()), request.getNewValue() == null ? null : request.getNewValue().trim());
        if (validation == null) {
            validation = ChangeValidation.withoutField(List.of(), List.of());
        }
        if (changeType == ChangeType.UPDATE && !validation.hasField()) {
            throw CommonErrors.fieldError("field_code", "请选择要修改的项目");
        }

        Map<String, ChangeValidation.FieldValue> beforeValues = ChangeRequestJson.fieldValues(form);
        ChangeRequest entity = buildEntity(request, bizType, changeType, record, form,
                member, userId, reason, validation, beforeValues);
        try {
            TenantSkip.run(() -> changeRequestMapper.insert(entity));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PENDING_EXISTS, MSG_ALREADY_PENDING);
        }
        handler.onSubmitted(entity);
        return convert.toVo(entity, farmName(entity.getFarmId(), userId), convert.brief(userId));
    }

    private ChangeRequest buildEntity(ChangeRequestSubmitRequest request, BizType bizType, ChangeType changeType,
                                      ChangeRecord record, ChangeForm form,
                                      FarmContext.FarmMember member, Long userId, String reason,
                                      ChangeValidation validation,
                                      Map<String, ChangeValidation.FieldValue> beforeValues) {
        ChangeRequest entity = new ChangeRequest();
        entity.setRequestNo(noGenerator.next());
        entity.setFarmId(record.farmId());
        entity.setBizType(bizType.name());
        entity.setBizId(record.bizId());
        entity.setSourceModule(ChangeBizTypes.sourceModuleOf(bizType).name());
        entity.setBatchId(record.batchId());
        entity.setPenId(record.penId());
        entity.setRecordLocator(truncate(record.recordLocator(), 200));
        entity.setChangeType(changeType.name());
        if (validation.hasField()) {
            entity.setFieldCode(truncate(validation.fieldCode(), 50));
            entity.setFieldName(truncate(validation.fieldName(), 50));
            entity.setValueUnit(truncate(validation.unit(), 20));
            entity.setBeforeDisplay(truncate(validation.beforeDisplay(), 200));
            entity.setAfterDisplay(truncate(validation.afterDisplay(), 200));
        }
        entity.setBeforeJson(ChangeRequestJson.beforeJson(record, form, beforeValues));
        entity.setAfterJson(ChangeRequestJson.afterJson(record, form, beforeValues, validation));
        entity.setRecordVersion(truncate(request.getRecordVersion(), 50));
        entity.setReason(reason);
        applyImpacts(entity, validation.impacts());
        entity.setRecalcTags(ChangeRequestJson.joinTags(validation.recalcTags()));
        entity.setStatus(ChangeStatus.PENDING.name());
        entity.setPendingFlag(1);
        entity.setSubmittedBy(userId);
        entity.setSubmitterRole(member.role() == null ? FarmRole.OWNER.name() : member.role().name());
        entity.setSubmittedAt(BeijingTime.now());
        entity.setSubmitChannel(submitChannel());
        entity.setResubmitOfId(request.getResubmitOfId());
        entity.setVersion(0);
        return entity;
    }

    /**
     * before/after JSON 中的字段定义来自 handler.describeForm，校验结果只补主修改项的新值。
     */
    private static void applyImpacts(ChangeRequest entity, List<ChangeImpact> impacts) {
        entity.setImpactStock(flag(impacts, ChangeImpact.STOCK));
        entity.setImpactCost(flag(impacts, ChangeImpact.COST));
        entity.setImpactInventory(flag(impacts, ChangeImpact.INVENTORY));
        entity.setImpactSellableDate(flag(impacts, ChangeImpact.SELLABLE_DATE));
        entity.setImpactIncome(flag(impacts, ChangeImpact.INCOME));
        entity.setImpactStats(flag(impacts, ChangeImpact.STATS));
    }

    private static int flag(List<ChangeImpact> impacts, ChangeImpact impact) {
        return impacts != null && impacts.contains(impact) ? 1 : 0;
    }

    private void checkPending(BizType bizType, Long bizId) {
        ChangeRequest pending = TenantSkip.call(() -> changeRequestMapper.selectPending(bizType.name(), bizId));
        if (pending != null) {
            throw new BizException(ErrorCode.PENDING_EXISTS, MSG_ALREADY_PENDING);
        }
    }

    private void checkResubmit(Long resubmitOfId, Long userId, BizType bizType, Long bizId) {
        if (resubmitOfId == null) {
            return;
        }
        ChangeRequest old = TenantSkip.call(() -> changeRequestMapper.selectByIdAny(resubmitOfId));
        if (old == null || !userId.equals(old.getSubmittedBy())
                || !ChangeStatus.REJECTED.name().equals(old.getStatus())
                || !bizType.name().equals(old.getBizType()) || !bizId.equals(old.getBizId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重新提交的申请不正确");
        }
        if (TenantSkip.call(() -> changeRequestMapper.countResubmitted(resubmitOfId)) > 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, MSG_RESUBMITTED);
        }
    }

    // ===== U03 =====

    public PageVO<ChangeRequestVO> list(ChangeRequestListQuery query) {
        Long userId = UserContext.requireUserId();
        ChangeRequestListQuery q = query == null ? new ChangeRequestListQuery() : query;
        String status = trim(q.getStatus());
        if (status.isEmpty()) {
            status = null;
        } else if (CodeEnum.of(ChangeStatus.class, status) == null) {
            throw CommonErrors.fieldError("status", "申请状态不正确");
        }
        BizType bizType = null;
        if (q.getBizType() != null && !q.getBizType().isBlank()) {
            bizType = parseBizType(q.getBizType());
        }
        String keyword = trim(q.getKeyword());
        if (keyword.isEmpty()) {
            keyword = null;
        } else if (keyword.length() > 30) {
            throw CommonErrors.fieldError("keyword", "搜索词不能超过 30 字");
        }
        checkStaffForList(userId);

        int page = q.pageOrDefault();
        int size = q.sizeOrDefault();
        int offset = (page - 1) * size;
        String statusFilter = status;
        BizType bizTypeFilter = bizType;
        String keywordFilter = keyword;
        List<ChangeRequest> rows = TenantSkip.call(() -> changeRequestMapper.selectMine(userId, statusFilter,
                bizTypeFilter == null ? null : bizTypeFilter.name(), q.getFarmId(), keywordFilter, offset, size));
        long total = TenantSkip.call(() -> changeRequestMapper.countMine(userId, statusFilter,
                bizTypeFilter == null ? null : bizTypeFilter.name(), q.getFarmId(), keywordFilter));
        Map<String, Integer> tabCounts = loadTabCounts(userId, bizTypeFilter, q.getFarmId(), keywordFilter);

        Set<Long> farmIds = new LinkedHashSet<>();
        Set<Long> submitters = new LinkedHashSet<>();
        for (ChangeRequest row : rows) {
            farmIds.add(row.getFarmId());
            submitters.add(row.getSubmittedBy());
        }
        Map<Long, String> farmNames = convert.farmNames(farmIds, userId);
        Map<Long, UserBriefVO> briefs = convert.briefs(submitters);
        List<ChangeRequestVO> list = new ArrayList<>(rows.size());
        for (ChangeRequest row : rows) {
            list.add(convert.toVo(row, farmNames.get(row.getFarmId()), briefs.get(row.getSubmittedBy())));
        }
        return PageVO.of(list, total, page, size).withTabCounts(tabCounts);
    }

    private Map<String, Integer> loadTabCounts(Long userId, BizType bizType, Long farmId, String keyword) {
        String typeCode = bizType == null ? null : bizType.name();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ALL", toInt(TenantSkip.call(() ->
                changeRequestMapper.countMine(userId, null, typeCode, farmId, keyword))));
        counts.put(ChangeStatus.PENDING.name(), toInt(TenantSkip.call(() ->
                changeRequestMapper.countMineByStatus(userId, ChangeStatus.PENDING.name(), typeCode, farmId, keyword))));
        counts.put(ChangeStatus.APPROVED.name(), toInt(TenantSkip.call(() ->
                changeRequestMapper.countMineByStatus(userId, ChangeStatus.APPROVED.name(), typeCode, farmId, keyword))));
        counts.put(ChangeStatus.REJECTED.name(), toInt(TenantSkip.call(() ->
                changeRequestMapper.countMineByStatus(userId, ChangeStatus.REJECTED.name(), typeCode, farmId, keyword))));
        return counts;
    }

    private static int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * 员工无权查看修改申请。FarmContext 只有当前养殖场，无法枚举全部养殖场：
     * 当前养殖场是员工且本人从未提交过申请时返回 40303；提交过申请（说明在某个场是所有者或场长）照常返回列表。
     */
    private void checkStaffForList(Long userId) {
        FarmContext.FarmMember member = FarmContext.get();
        if (member != null && member.isStaff()) {
            long mine = TenantSkip.call(() -> changeRequestMapper.countMine(userId, null, null, null, null));
            if (mine == 0) {
                throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_STAFF_FORBIDDEN);
            }
        }
    }

    // ===== U04 =====

    public ChangeRequestVO detail(Long id) {
        Long userId = UserContext.requireUserId();
        if (id == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_DETAIL_NOT_FOUND);
        }
        ChangeRequest request = TenantSkip.call(() -> changeRequestMapper.selectByIdAny(id));
        if (request == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_DETAIL_NOT_FOUND);
        }
        if (!canViewDetail(request, userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_DETAIL_NOT_FOUND);
        }
        return convert.toVo(request, farmName(request.getFarmId(), userId), convert.brief(request.getSubmittedBy()));
    }

    /** 提交人本人，或申请所属养殖场的当前所有者；该场的员工返回 40303 */
    private boolean canViewDetail(ChangeRequest request, Long userId) {
        if (userId.equals(request.getSubmittedBy())) {
            return true;
        }
        FarmContext.FarmMember member = farmMemberCache.get(request.getFarmId(), userId);
        if (member == null) {
            return false;
        }
        if (member.role() == FarmRole.STAFF) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_STAFF_FORBIDDEN);
        }
        return member.role() == FarmRole.OWNER;
    }

    // ===== 公用 =====

    private ChangeRecord loadRecord(ChangeRequestHandler handler, BizType bizType, Long bizId) {
        ChangeRecord record = handler.loadRecord(bizType, bizId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        return record;
    }

    private FarmContext.FarmMember requireManager(Long farmId, Long userId) {
        if (farmId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        FarmContext.FarmMember member = farmMemberCache.get(farmId, userId);
        if (member == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        if (member.role() == FarmRole.STAFF) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, MSG_STAFF_FORBIDDEN);
        }
        checkFarmStatus(member);
        return member;
    }

    private static void checkFarmStatus(FarmContext.FarmMember member) {
        FarmStatus status = member.farmStatus();
        if (status == null || status == FarmStatus.NORMAL) {
            return;
        }
        if (status == FarmStatus.FROZEN) {
            String name = member.farmName() == null || member.farmName().isBlank() ? "养殖场" : member.farmName();
            throw new BizException(ErrorCode.FARM_FROZEN,
                    name + "已被平台冻结，暂时不能使用。原因见消息中心");
        }
        String message = switch (status) {
            case PENDING -> "养殖场正在审核中，审核通过后才能提交修改申请";
            case REJECTED -> "养殖场审核未通过，请修改后重新提交";
            case DISABLED -> "养殖场已停用，重新启用后才能提交修改申请";
            default -> ErrorCode.FARM_STATUS_FORBIDDEN.getDefaultMessage();
        };
        throw new BizException(ErrorCode.FARM_STATUS_FORBIDDEN, message);
    }

    private String farmName(Long farmId, Long userId) {
        return convert.farmNames(List.of(farmId), userId).get(farmId);
    }

    private static String submitChannel() {
        ClientType client = RequestContext.clientType();
        return client == ClientType.WEB ? "WEB" : "APP_ANDROID";
    }

    private static BizType parseBizType(String code) {
        BizType type = CodeEnum.of(BizType.class, code == null ? null : code.trim());
        if (type == null) {
            throw CommonErrors.fieldError("biz_type", "记录类型不正确");
        }
        return type;
    }

    /** U02 的 field_errors 键只用 field_code / new_value / reason，记录类型与 ID 错误只给 message */
    private static BizType parseBizTypeForSubmit(String code) {
        BizType type = CodeEnum.of(BizType.class, code == null ? null : code.trim());
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "记录类型不正确");
        }
        return type;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
