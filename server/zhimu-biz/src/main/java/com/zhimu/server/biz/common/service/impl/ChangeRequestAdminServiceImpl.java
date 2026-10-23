package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.common.change.ChangeApplyResult;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestHandlerRegistry;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.convert.ChangeRequestConvert;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.ChangeStatus;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.tx.AfterCommit;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理端修改申请审核（ADM-R12 / R13）：占用申请（状态 + version）、重取原记录比对版本、来源模块回写与重算、
 * 写 t_operation_log、事务提交后给提交人发结果消息。任一步失败整体回滚，申请仍为 PENDING。
 * 第 1 批只出 Service，不写 /admin/v1 Controller。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeRequestAdminServiceImpl implements ChangeRequestAdminService {

    public static final String MSG_APPROVE_EMPTY = "请填写审核意见";
    public static final String MSG_REJECT_EMPTY = "请填写驳回原因";
    public static final String MSG_RECORD_CHANGED = "原记录已变化，这条申请不能通过，请驳回并说明原因。";
    public static final String MSG_RECORD_DELETED = "原记录已被删除，这条申请不能通过，请驳回并说明原因。";

    private static final int TEXT_MAX = 200;

    private final ChangeRequestMapper changeRequestMapper;
    private final ChangeRequestHandlerRegistry handlerRegistry;
    private final OperationLogRecorder operationLogRecorder;
    private final ObjectProvider<MessageService> messageService;

    @Override
    @Transactional
    public void approve(Long id, Long adminId, String opinion, int version) {
        String comment = trim(opinion);
        if (comment.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, MSG_APPROVE_EMPTY);
        }
        if (comment.length() > TEXT_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核意见不能超过 200 字");
        }
        ChangeRequest request = loadPending(id, version);
        BizType bizType = parseBizType(request.getBizType());
        ChangeRequestHandler handler = handlerRegistry.require(bizType);
        LocalDateTime now = BeijingTime.now();
        int claimed = TenantSkip.call(() ->
                changeRequestMapper.claimApprove(request.getId(), version, adminId, comment, now));
        if (claimed == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, ChangeRequestApplicationService.MSG_ALREADY_HANDLED);
        }
        request.setStatus(ChangeStatus.APPROVED.name());
        request.setReviewComment(comment);
        request.setReviewedAt(now);
        request.setReviewedBy(adminId);
        ChangeRecord record = handler.loadRecordForUpdate(bizType, request.getBizId());
        if (record == null || record.deleted() || record.voided()) {
            throw new BizException(ErrorCode.RECORD_DELETED, MSG_RECORD_DELETED);
        }
        if (!Objects.equals(record.recordVersion(), request.getRecordVersion())) {
            throw new BizException(ErrorCode.STATE_CHANGED, MSG_RECORD_CHANGED).with("reason", "RECORD_CHANGED");
        }
        handler.validateForApprove(request, record);
        ChangeApplyResult result = handler.apply(request, record, adminId);
        String impactJson = ChangeRequestJson.impactJson(impactsOf(request),
                ChangeRequestJson.splitTags(request.getRecalcTags()), result, "PASS", null);
        TenantSkip.run(() -> changeRequestMapper.updateResult(request.getId(),
                result == null ? null : result.recalcSummary(),
                result == null ? null : result.recalcCount(), impactJson, BeijingTime.now()));
        writeLog(request, adminId, "APPROVE", comment, null, result);
        handler.onClosed(request);
        notifyAfterCommit(request, true);
    }

    @Override
    @Transactional
    public void reject(Long id, Long adminId, String reason, int version) {
        String rejectReason = trim(reason);
        if (rejectReason.isEmpty()) {
            throw new BizException(ErrorCode.REASON_EMPTY, MSG_REJECT_EMPTY);
        }
        if (rejectReason.length() > TEXT_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回原因不能超过 200 字");
        }
        ChangeRequest request = loadPending(id, version);
        LocalDateTime now = BeijingTime.now();
        int rows = TenantSkip.call(() ->
                changeRequestMapper.markRejected(request.getId(), version, adminId, rejectReason, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CHANGED, ChangeRequestApplicationService.MSG_ALREADY_HANDLED);
        }
        request.setStatus(ChangeStatus.REJECTED.name());
        request.setRejectReason(rejectReason);
        request.setReviewedAt(now);
        request.setReviewedBy(adminId);
        writeLog(request, adminId, "REJECT", rejectReason, rejectReason, null);
        ChangeRequestHandler handler = handlerRegistry.get(parseBizTypeOrNull(request.getBizType()));
        if (handler != null) {
            handler.onClosed(request);
        }
        notifyAfterCommit(request, false);
    }

    @Override
    public long countPending() {
        return TenantSkip.call(() -> changeRequestMapper.countPending());
    }

    private ChangeRequest loadPending(Long id, int version) {
        ChangeRequest request = id == null ? null : TenantSkip.call(() -> changeRequestMapper.selectByIdAny(id));
        if (request == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (request.getVersion() != null && request.getVersion() != version) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        if (!ChangeStatus.PENDING.name().equals(request.getStatus())) {
            throw new BizException(ErrorCode.STATE_CHANGED, ChangeRequestApplicationService.MSG_ALREADY_HANDLED);
        }
        return request;
    }

    private void writeLog(ChangeRequest request, Long adminId, String action, String reason, String extraReason,
                          ChangeApplyResult result) {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        before.put("status", Map.of("name", "申请状态", "display", "待审核"));
        after.put("status", Map.of("name", "申请状态",
                "display", "APPROVE".equals(action) ? "已通过" : "已驳回"));
        String extra = null;
        if (result != null && result.recalcSummary() != null) {
            extra = result.recalcSummary();
        } else if (result != null && result.recalcCount() != null) {
            extra = "已重新计算 " + result.recalcCount() + " 项";
        }
        OperationLogEntry entry = OperationLogEntry.builder()
                .operatorType("ADMIN")
                .operatorId(adminId)
                .opType("CHANGE_REQUEST")
                .opAction(action)
                .result("SUCCESS")
                .bizType(BizType.CHANGE_REQUEST.name())
                .bizId(request.getId())
                .farmId(request.getFarmId())
                .targetName(targetName(request))
                .reason(reason)
                .extraText(extra)
                .before(before)
                .after(after)
                .changeRequestId(request.getId())
                .build();
        try {
            operationLogRecorder.record(entry);
        } catch (RuntimeException e) {
            log.error("修改申请审核留痕失败 request_id={}", request.getId(), e);
            throw e;
        }
    }

    private static String targetName(ChangeRequest request) {
        StringBuilder text = new StringBuilder();
        String typeText = ChangeRequestConvert.bizTypeText(request.getBizType());
        if (typeText != null) {
            text.append(typeText);
        }
        if (request.getFieldName() != null) {
            text.append(text.isEmpty() ? "" : " · ").append(request.getFieldName());
        }
        if (request.getRecordLocator() != null) {
            text.append(text.isEmpty() ? "" : " · ").append(request.getRecordLocator());
        }
        if (request.getRequestNo() != null) {
            text.append("（").append(request.getRequestNo()).append("）");
        }
        String value = text.toString();
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private void notifyAfterCommit(ChangeRequest request, boolean approved) {
        AfterCommit.run(() -> {
            MessageService service = messageService.getIfAvailable();
            if (service == null) {
                return;
            }
            try {
                String summary = summaryOf(request);
                service.send(MessageCommand.builder()
                        .msgType(MsgType.CHANGE_REQ_RESULT)
                        .level(MsgLevel.NORMAL)
                        .farmId(request.getFarmId())
                        .receiverUserId(request.getSubmittedBy())
                        .title(approved ? "修改申请已通过" : "修改申请已驳回")
                        .summary(summary)
                        .content(approved ? request.getReviewComment() : request.getRejectReason())
                        .bizType(BizType.CHANGE_REQUEST)
                        .bizId(request.getId())
                        .route("U02")
                        .build());
            } catch (RuntimeException e) {
                log.warn("修改申请结果消息发送失败 request_id={}", request.getId(), e);
            }
        });
    }

    private static String summaryOf(ChangeRequest request) {
        StringBuilder text = new StringBuilder();
        String typeText = ChangeRequestConvert.bizTypeText(request.getBizType());
        if (typeText != null) {
            text.append(typeText);
        }
        if (request.getFieldName() != null) {
            text.append(text.isEmpty() ? "" : " · ").append(request.getFieldName());
        }
        if (request.getBeforeDisplay() != null || request.getAfterDisplay() != null) {
            text.append(" ").append(request.getBeforeDisplay() == null ? "" : request.getBeforeDisplay())
                    .append(" → ").append(request.getAfterDisplay() == null ? "" : request.getAfterDisplay());
        }
        return text.toString();
    }

    private static List<ChangeImpact> impactsOf(ChangeRequest request) {
        List<ChangeImpact> impacts = new ArrayList<>();
        add(impacts, request.getImpactStock(), ChangeImpact.STOCK);
        add(impacts, request.getImpactCost(), ChangeImpact.COST);
        add(impacts, request.getImpactInventory(), ChangeImpact.INVENTORY);
        add(impacts, request.getImpactSellableDate(), ChangeImpact.SELLABLE_DATE);
        add(impacts, request.getImpactIncome(), ChangeImpact.INCOME);
        add(impacts, request.getImpactStats(), ChangeImpact.STATS);
        return impacts;
    }

    private static void add(List<ChangeImpact> impacts, Integer flag, ChangeImpact impact) {
        if (flag != null && flag == 1) {
            impacts.add(impact);
        }
    }

    private static BizType parseBizType(String code) {
        BizType type = parseBizTypeOrNull(code);
        if (type == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED, "该记录类型暂不支持提交修改申请");
        }
        return type;
    }

    private static BizType parseBizTypeOrNull(String code) {
        return CodeEnum.of(BizType.class, code);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
