package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.ChangeRequestAdminQuery;
import com.zhimu.server.admin.audit.query.ChangeRequestHistoryRow;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.request.ChangeRequestApproveRequest;
import com.zhimu.server.admin.audit.service.ChangeRequestReviewService;
import com.zhimu.server.admin.audit.support.AuditReasonTemplates;
import com.zhimu.server.admin.audit.support.AuditSupport;
import com.zhimu.server.admin.audit.support.AuditTexts;
import com.zhimu.server.admin.audit.support.AuditWaitCalculator;
import com.zhimu.server.admin.audit.vo.ApproveResultVO;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminDetailVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminItemVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminPageVO;
import com.zhimu.server.admin.audit.vo.CompareRowVO;
import com.zhimu.server.admin.audit.vo.ReviewVO;
import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.common.change.ChangeRecord;
import com.zhimu.server.biz.common.change.ChangeRequestHandler;
import com.zhimu.server.biz.common.change.ChangeRequestHandlerRegistry;
import com.zhimu.server.biz.common.change.ChangeRequestJson;
import com.zhimu.server.biz.common.convert.ChangeRequestConvert;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ChangeImpact;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** ADM-R10–R13：只做队列/详情组装，通过时的回写与重算全部交给既有 handler 与 ChangeRequestAdminService。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeRequestReviewServiceImpl implements ChangeRequestReviewService {

    private static final String MSG_NOT_FOUND = "这条修改申请不存在，列表已刷新";
    private static final String MSG_NOTIFIED = "结果发到提交人的消息中心，并显示在“我的修改申请”";
    private static final String EMPTY_DISPLAY = "未填写";

    private final AuditAdminQueryMapper queryMapper;
    private final ChangeRequestAdminService changeRequestAdminService;
    private final ChangeRequestHandlerRegistry handlerRegistry;

    @Override
    public ChangeRequestAdminPageVO list(ChangeRequestAdminQuery query) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String status = query.statusOrDefault();
        String keyword = trimToNull(query.getKeyword());
        long total = queryMapper.countChangeRequests(status, keyword, query.getSourceModule(), query.getImpact());
        List<ChangeRequestAdminItemVO> rows = total == 0 ? List.of()
                : queryMapper.selectChangeRequests(status, keyword, query.getSourceModule(), query.getImpact(),
                offset, size);
        LocalDateTime now = BeijingTime.now();
        for (ChangeRequestAdminItemVO row : rows) {
            row.setBizTypeText(ChangeRequestConvert.bizTypeText(row.getBizType()));
            row.setSourceModuleText(AuditTexts.sourceModuleText(row.getSourceModule()));
            row.setChangeTypeText(AuditTexts.changeTypeText(row.getChangeType()));
            row.setSubmitterRoleText(AuditTexts.roleText(row.getSubmitterRole()));
            row.setStatusText(AuditTexts.changeStatusText(row.getStatus()));
            row.setTitle(title(row));
            row.setImpacts(impacts(row));
            AuditWaitCalculator.Wait wait = AuditWaitCalculator.of(row.getSubmittedAt(), now);
            row.setWaitMinutes(wait.minutes());
            row.setWaitText(wait.text());
            row.setOverdue(wait.overdue());
        }
        ChangeRequestAdminPageVO result = new ChangeRequestAdminPageVO(rows, total, page, size);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        for (String code : List.of("PENDING", "APPROVED", "REJECTED")) {
            tabs.put(code, (int) queryMapper.countChangeRequestsByStatus(code, keyword,
                    query.getSourceModule(), query.getImpact()));
        }
        result.setTabCounts(tabs);
        result.setSellableDateCount((int) queryMapper.countSellableDatePending());
        return result;
    }

    @Override
    public ChangeRequestAdminDetailVO detail(Long id) {
        ChangeRequest request = id == null ? null : queryMapper.selectChangeRequest(id);
        if (request == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        String farmName = queryMapper.selectFarmName(request.getFarmId());
        String submitterName = queryMapper.selectUserName(request.getSubmittedBy());
        String reviewerName = request.getReviewedBy() == null ? null
                : queryMapper.selectAdminName(request.getReviewedBy());
        String batchNo = request.getBatchId() == null ? null : queryMapper.selectBatchNo(request.getBatchId());
        JsonNode beforeRoot = ChangeRequestJson.readTree(request.getBeforeJson());
        JsonNode afterRoot = ChangeRequestJson.readTree(request.getAfterJson());
        ChangeRequestAdminDetailVO.Validation validation = validation(request);
        return ChangeRequestAdminDetailVO.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .status(request.getStatus())
                .statusText(AuditTexts.changeStatusText(request.getStatus()))
                .version(request.getVersion())
                .changeType(request.getChangeType())
                .changeTypeText(AuditTexts.changeTypeText(request.getChangeType()))
                .header(ChangeRequestAdminDetailVO.Header.builder()
                        .title(title(request))
                        .farmName(farmName)
                        .submitterName(submitterName)
                        .submitterRoleText(AuditTexts.roleText(request.getSubmitterRole()))
                        .submittedAt(request.getSubmittedAt())
                        .submitChannelText(AuditTexts.submitChannelText(request.getSubmitChannel()))
                        .build())
                .original(original(request, beforeRoot))
                .compare(compare(request, beforeRoot, afterRoot))
                .reason(request.getReason())
                .impact(impact(request))
                .validation(validation)
                .recordHistory(history(request))
                .batchId(request.getBatchId())
                .batchNo(batchNo)
                .notifyText(request.getSubmittedBy() == null ? MSG_NOTIFIED
                        : "结果发到" + nullSafe(submitterName) + "的消息中心，并显示在“我的修改申请”")
                .review(review(request, reviewerName))
                .build();
    }

    @Override
    public ApproveResultVO approve(Long id, ChangeRequestApproveRequest request) {
        String comment = AuditSupport.requireOpinion(request.getReviewComment(), "请填写审核意见");
        ChangeRequest before = id == null ? null : queryMapper.selectChangeRequest(id);
        if (before == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        changeRequestAdminService.approve(id, AdminContext.requireAdminId(), comment, request.getVersion());
        ChangeRequest after = queryMapper.selectChangeRequest(id);
        String submitterName = after == null ? null : queryMapper.selectUserName(after.getSubmittedBy());
        return ApproveResultVO.builder()
                .recalcCount(after == null ? null : after.getRecalcCount())
                .recalcSummary(after == null ? null : after.getRecalcSummary())
                .notifiedNames(submitterName == null ? List.of() : List.of(submitterName))
                .build();
    }

    @Override
    public AuditResultVO reject(Long id, AuditRejectRequest request) {
        String reason = AuditSupport.requireReason(request.getRejectReason(), "请填写驳回原因");
        ChangeRequest change = id == null ? null : queryMapper.selectChangeRequest(id);
        if (change == null) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_NOT_FOUND);
        }
        changeRequestAdminService.reject(id, AdminContext.requireAdminId(), reason, request.getVersion());
        String farmName = queryMapper.selectFarmName(change.getFarmId());
        String submitterName = queryMapper.selectUserName(change.getSubmittedBy());
        return AuditResultVO.builder()
                .targetName(farmName)
                .notifiedNames(submitterName == null ? List.of() : List.of(submitterName))
                .build();
    }

    private ChangeRequestAdminDetailVO.Validation validation(ChangeRequest request) {
        if (!"PENDING".equals(request.getStatus())) {
            return snapshotValidation(request);
        }
        BizType bizType = CodeEnum.of(BizType.class, request.getBizType());
        ChangeRequestHandler handler = handlerRegistry.get(bizType);
        if (handler == null) {
            return ChangeRequestAdminDetailVO.Validation.builder()
                    .level("BLOCK")
                    .text("该记录类型暂不支持审核通过，请驳回并说明原因。")
                    .build();
        }
        ChangeRecord record;
        try {
            record = handler.loadRecord(bizType, request.getBizId());
        } catch (RuntimeException e) {
            log.warn("修改申请详情读取原记录失败 request_id={}", request.getId(), e);
            return ChangeRequestAdminDetailVO.Validation.builder()
                    .level("BLOCK")
                    .text("原记录读取失败，这条申请不能通过，请驳回并说明原因。")
                    .build();
        }
        if (record == null || record.deleted() || record.voided()) {
            return ChangeRequestAdminDetailVO.Validation.builder()
                    .level("BLOCK")
                    .text("原记录已被删除，这条申请不能通过，请驳回并说明原因。")
                    .build();
        }
        if (!Objects.equals(record.recordVersion(), request.getRecordVersion())) {
            return ChangeRequestAdminDetailVO.Validation.builder()
                    .level("BLOCK")
                    .text("原记录已变化，这条申请不能通过，请驳回并说明原因。")
                    .errorCode("RECORD_CHANGED")
                    .build();
        }
        try {
            handler.validateForApprove(request, record);
        } catch (BizException e) {
            return ChangeRequestAdminDetailVO.Validation.builder()
                    .level("BLOCK")
                    .text(e.getMessage())
                    .errorCode(String.valueOf(e.getCode()))
                    .build();
        }
        return ChangeRequestAdminDetailVO.Validation.builder()
                .level("PASS")
                .text("系统校验通过：原记录未变化，可以按申请内容修改并重新计算。")
                .build();
    }

    private ChangeRequestAdminDetailVO.Validation snapshotValidation(ChangeRequest request) {
        JsonNode root = ChangeRequestJson.readTree(request.getImpactJson());
        JsonNode validation = root.path("validation");
        String level = text(validation, "level");
        String message = text(validation, "text");
        return ChangeRequestAdminDetailVO.Validation.builder()
                .level(level == null ? ("APPROVED".equals(request.getStatus()) ? "PASS" : "WARN") : level)
                .text(message)
                .errorCode(text(validation, "error_code"))
                .build();
    }

    private ChangeRequestAdminDetailVO.Impact impact(ChangeRequest request) {
        JsonNode root = ChangeRequestJson.readTree(request.getImpactJson());
        List<ChangeRequestAdminDetailVO.ImpactRow> rows = new ArrayList<>();
        JsonNode rowNodes = root.path("rows");
        if (rowNodes.isArray()) {
            for (JsonNode node : rowNodes) {
                rows.add(ChangeRequestAdminDetailVO.ImpactRow.builder()
                        .item(text(node, "item"))
                        .current(text(node, "current"))
                        .after(text(node, "after"))
                        .isLoss(node.path("is_loss").asBoolean(false))
                        .build());
            }
        }
        List<String> tags = ChangeRequestJson.splitTags(request.getRecalcTags());
        String note = text(root, "note");
        if (note == null) {
            note = tags.isEmpty() ? "通过后系统按来源模块规则重新计算相关数据。"
                    : "通过后系统重新计算：" + String.join("、", tags) + "。";
        }
        return ChangeRequestAdminDetailVO.Impact.builder()
                .note(note)
                .rows(rows)
                .recalcTags(tags)
                .build();
    }

    private List<ChangeRequestAdminDetailVO.Kv> original(ChangeRequest request, JsonNode beforeRoot) {
        List<ChangeRequestAdminDetailVO.Kv> rows = new ArrayList<>();
        JsonNode card = beforeRoot.path("record_card");
        if (card.isArray()) {
            for (JsonNode item : card) {
                String label = text(item, "label");
                String value = text(item, "value");
                if (label != null || value != null) {
                    rows.add(ChangeRequestAdminDetailVO.Kv.builder()
                            .label(label)
                            .value(value)
                            .span(1)
                            .build());
                }
            }
        }
        if (request.getFieldName() != null) {
            rows.add(ChangeRequestAdminDetailVO.Kv.builder()
                    .label("修改项")
                    .value(request.getFieldName())
                    .span(1)
                    .build());
        }
        if (request.getRecordLocator() != null) {
            rows.add(ChangeRequestAdminDetailVO.Kv.builder()
                    .label("记录定位")
                    .value(request.getRecordLocator())
                    .span(3)
                    .build());
        }
        return rows;
    }

    private List<CompareRowVO> compare(ChangeRequest request, JsonNode beforeRoot, JsonNode afterRoot) {
        Map<String, Field> before = fields(beforeRoot.path("before"));
        Map<String, Field> after = fields(afterRoot.path("after"));
        List<CompareRowVO> rows = new ArrayList<>();
        if ("DELETE".equals(request.getChangeType())) {
            rows.add(CompareRowVO.of("记录状态", "有效", "已删除"));
        } else if ("VOID".equals(request.getChangeType())) {
            rows.add(CompareRowVO.of("记录状态", "有效", "已作废"));
        }
        Set<String> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        for (String key : keys) {
            Field b = before.get(key);
            Field a = after.get(key);
            String name = a != null && a.name != null ? a.name : b == null ? key : b.name;
            String beforeText = b == null ? null : b.display;
            String afterText = a == null ? null : a.display;
            if (beforeText == null && afterText == null) {
                continue;
            }
            if (a == null) {
                // after 快照只写改动项：没写进 after 的字段就是没改，原值照列但不算修改
                rows.add(new CompareRowVO(name, beforeText, beforeText, false));
                continue;
            }
            if (afterText == null) {
                // 改动项清空了值（display 缺省），显示“未填写”而不是回落成原值
                afterText = EMPTY_DISPLAY;
            }
            rows.add(CompareRowVO.of(name, beforeText, afterText));
        }
        if (rows.isEmpty() && request.getBeforeDisplay() != null) {
            rows.add(CompareRowVO.of(request.getFieldName() == null ? "修改项" : request.getFieldName(),
                    request.getBeforeDisplay(), request.getAfterDisplay()));
        }
        return rows;
    }

    private static Map<String, Field> fields(JsonNode node) {
        Map<String, Field> fields = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return fields;
        }
        var names = node.propertyNames().iterator();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = node.get(key);
            if (value == null || value.isNull() || !value.isObject()) {
                continue;
            }
            fields.put(key, new Field(text(value, "name"), text(value, "display")));
        }
        return fields;
    }

    private List<ChangeRequestAdminDetailVO.RecordHistory> history(ChangeRequest request) {
        List<ChangeRequestHistoryRow> rows = queryMapper.selectChangeRequestHistory(request.getBizType(),
                request.getBizId(), request.getId(), 20);
        List<ChangeRequestAdminDetailVO.RecordHistory> history = new ArrayList<>();
        for (ChangeRequestHistoryRow row : rows) {
            history.add(ChangeRequestAdminDetailVO.RecordHistory.builder()
                    .fieldName(row.getFieldName())
                    .beforeDisplay(row.getBeforeDisplay())
                    .afterDisplay(row.getAfterDisplay())
                    .reviewerName(row.getReviewerName())
                    .reviewedAt(row.getReviewedAt())
                    .statusText(AuditTexts.changeStatusText(row.getStatus()))
                    .recalcSummary(row.getRecalcSummary())
                    .build());
        }
        return history;
    }

    private ReviewVO review(ChangeRequest request, String reviewerName) {
        if (request.getReviewedAt() == null || "PENDING".equals(request.getStatus())) {
            return null;
        }
        return ReviewVO.builder()
                .result(request.getStatus())
                .resultText(AuditTexts.changeStatusText(request.getStatus()))
                .reviewerName(reviewerName)
                .reviewedAt(request.getReviewedAt())
                .opinion(request.getReviewComment())
                .rejectReason(request.getRejectReason())
                .build();
    }

    private static String title(ChangeRequestAdminItemVO row) {
        String type = row.getBizTypeText() == null ? row.getBizType() : row.getBizTypeText();
        if ("DELETE".equals(row.getChangeType())) {
            return type + " · 删除";
        }
        if ("VOID".equals(row.getChangeType())) {
            return type + " · 作废";
        }
        return row.getFieldName() == null ? type : type + " · " + row.getFieldName();
    }

    private static String title(ChangeRequest request) {
        String type = ChangeRequestConvert.bizTypeText(request.getBizType());
        if (type == null) {
            type = request.getBizType();
        }
        if ("DELETE".equals(request.getChangeType())) {
            return type + " · 删除";
        }
        if ("VOID".equals(request.getChangeType())) {
            return type + " · 作废";
        }
        return request.getFieldName() == null ? type : type + " · " + request.getFieldName();
    }

    private static List<ChangeRequestAdminItemVO.ImpactTag> impacts(ChangeRequestAdminItemVO row) {
        List<ChangeRequestAdminItemVO.ImpactTag> impacts = new ArrayList<>();
        addImpact(impacts, row.getImpactStock(), ChangeImpact.STOCK);
        addImpact(impacts, row.getImpactInventory(), ChangeImpact.INVENTORY);
        addImpact(impacts, row.getImpactCost(), ChangeImpact.COST);
        addImpact(impacts, row.getImpactSellableDate(), ChangeImpact.SELLABLE_DATE);
        addImpact(impacts, row.getImpactIncome(), ChangeImpact.INCOME);
        addImpact(impacts, row.getImpactStats(), ChangeImpact.STATS);
        return impacts;
    }

    private static void addImpact(List<ChangeRequestAdminItemVO.ImpactTag> impacts, Integer flag,
                                  ChangeImpact impact) {
        if (flag != null && flag == 1) {
            impacts.add(ChangeRequestAdminItemVO.ImpactTag.builder()
                    .code(impact.name())
                    .text(impact.getText())
                    .warn(impact == ChangeImpact.SELLABLE_DATE)
                    .build());
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record Field(String name, String display) {
    }
}
