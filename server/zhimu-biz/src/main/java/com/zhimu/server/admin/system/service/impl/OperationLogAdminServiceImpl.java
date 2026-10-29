package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.readonly.AdminOperationLogQueryMapper;
import com.zhimu.server.admin.system.query.OperationLogQuery;
import com.zhimu.server.admin.system.service.OperationLogAdminService;
import com.zhimu.server.admin.system.support.AdminLogTexts;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import com.zhimu.server.admin.system.vo.OperationLogItemVO;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 操作日志查询（ADM-S30、S31）。
 * <p>
 * 可见性（管理端-04 2.7.5）：审核类需对应审核页 HANDLE；内容类需对应内容页 HANDLE；
 * 基础数据任一 HANDLE；USER_STATUS、FARM_FREEZE、LOGIN、ADMIN_RBAC 分别需 S03、S04、S05 的 HANDLE；
 * EXPORT 需被导出页面的 HANDLE；操作人是本人时始终可见；超级管理员看到全部。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogAdminServiceImpl implements OperationLogAdminService {

    private static final int MAX_SPAN_DAYS = 366;
    private static final int DEFAULT_DAYS = 7;
    private static final int RELATED_LIMIT = 10;
    private static final String MSG_DATE_SPAN = "日期范围不能超过 1 年";
    private static final String MSG_DATE_ORDER = "开始日期不能晚于结束日期";
    private static final String MSG_LOG_NOT_FOUND = "该日志不存在";

    private final AdminOperationLogQueryMapper operationLogQueryMapper;
    private final AdminMapper adminMapper;

    @Override
    public PageVO<OperationLogItemVO> list(OperationLogQuery query) {
        Long viewerId = AdminContext.requireAdminId();
        Visibility visibility = visibility();
        LocalDate dateTo = query.getDateTo() == null ? BeijingTime.today() : query.getDateTo();
        LocalDate dateFrom = query.getDateFrom() == null ? dateTo.minusDays(DEFAULT_DAYS - 1) : query.getDateFrom();
        validateSpan(dateFrom, dateTo);
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        int offset = (page - 1) * size;
        String keyword = query.getKeyword() == null || query.getKeyword().isBlank() ? null : query.getKeyword().trim();

        List<String> opTypes = AdminLogTexts.expandOpType(query.getOpType());
        long total = operationLogQueryMapper.countAdminLogs(viewerId, visibility.allVisible(),
                visibility.allowedOpTypes(), dateFrom, dateTo, query.getAdminId(), opTypes, keyword);
        List<OperationLogItemVO> list = new ArrayList<>();
        if (total > 0) {
            List<OperationLog> rows = operationLogQueryMapper.selectAdminLogs(viewerId, visibility.allVisible(),
                    visibility.allowedOpTypes(), dateFrom, dateTo, query.getAdminId(), opTypes, keyword,
                    offset, size);
            for (OperationLog row : rows) {
                list.add(toItem(row));
            }
        }
        return PageVO.of(list, total, page, size);
    }

    @Override
    public OperationLogDetailVO detail(Long logId) {
        Long viewerId = AdminContext.requireAdminId();
        OperationLog row = logId == null ? null : operationLogQueryMapper.selectLogById(logId);
        if (row == null || !visible(row, viewerId, visibility())) {
            throw new BizException(ErrorCode.NOT_FOUND, MSG_LOG_NOT_FOUND);
        }
        Admin admin = row.getOperatorId() == null ? null : adminMapper.selectById(row.getOperatorId());
        List<OperationLogDetailVO.Related> related = related(row);
        return OperationLogDetailVO.builder()
                .operator(OperationLogDetailVO.Operator.builder()
                        .adminId(row.getOperatorId())
                        .name(row.getOperatorName() != null ? row.getOperatorName()
                                : admin == null ? null : admin.getName())
                        .username(admin == null ? null : admin.getUsername())
                        .role(row.getOperatorRole())
                        .roleText(AdminLogTexts.roleText(row.getOperatorRole()))
                        .build())
                .opType(row.getOpType())
                .opTypeText(AdminLogTexts.opTypeText(row.getOpType()))
                .opAction(row.getOpAction())
                .opActionText(AdminLogTexts.opActionText(row.getOpAction()))
                .result(row.getResult())
                .resultText(AdminLogTexts.resultText(row.getResult()))
                .targetName(row.getTargetName())
                .reason(row.getReason())
                .extraText(row.getExtraText())
                .notifyText(row.getNotifyText())
                .client(OperationLogDetailVO.Client.builder()
                        .clientType(row.getClientType())
                        .clientTypeText(AdminLogTexts.clientTypeText(row.getClientType()))
                        .browser(AdminLogTexts.browser(row.getUserAgent()))
                        .ipRegion(row.getIpRegion())
                        .build())
                .changes(changes(row.getBeforeJson(), row.getAfterJson()))
                .related(related)
                .link(link(row.getBizType(), row.getBizId()))
                .build();
    }

    /** 日志详情“打开对应记录”：页面编号 + 定位参数；查不到定位所需的数据时 exists = false。 */
    OperationLogDetailVO.Link link(String bizType, Long bizId) {
        String pageCode = AdminLogTexts.pageCodeOfBizType(bizType);
        Map<String, String> query = pageCode == null || bizId == null ? null : linkQuery(bizType, bizId);
        return OperationLogDetailVO.Link.builder()
                .pageCode(pageCode)
                .bizType(bizType)
                .bizId(bizId)
                .exists(pageCode != null && query != null)
                .query(query)
                .build();
    }

    private Map<String, String> linkQuery(String bizType, Long bizId) {
        Map<String, String> query = new LinkedHashMap<>();
        String id = String.valueOf(bizId);
        switch (bizType) {
            case "COURSE" -> query.put("course_id", id);
            case "KNOWLEDGE_TASK" -> query.put("task_id", id);
            case "CATEGORY" -> query.put("category_id", id);
            case "BREED" -> query.put("breed_id", id);
            case "STD_WEIGHT", "FEED_STD" -> {
                Long breedId = "STD_WEIGHT".equals(bizType)
                        ? operationLogQueryMapper.selectStdWeightBreedId(bizId)
                        : operationLogQueryMapper.selectFeedStdBreedId(bizId);
                if (breedId == null) {
                    return null;
                }
                query.put("breed_id", String.valueOf(breedId));
                query.put("tab", "STD_WEIGHT".equals(bizType) ? "weight" : "feed");
            }
            case "BANNED_DRUG" -> {
                query.put("tab", "BANNED");
                String name = operationLogQueryMapper.selectBannedDrugName(bizId);
                if (name != null && !name.isBlank()) {
                    query.put("keyword", name);
                }
            }
            case "DRUG_LOT_RECALL" -> {
                query.put("tab", "RECALL");
                query.put("id", id);
            }
            case "DICT_ITEM" -> {
                String dictType = operationLogQueryMapper.selectDictItemType(bizId);
                if (dictType == null) {
                    return null;
                }
                query.put("dict_type", dictType);
                query.put("id", id);
            }
            case "FINANCE_CATEGORY" -> {
                String direction = operationLogQueryMapper.selectFinanceCategoryDirection(bizId);
                if (direction == null) {
                    return null;
                }
                query.put("dict_type", "EXPENSE".equals(direction) ? "EXPENSE_CATEGORY" : "INCOME_CATEGORY");
                query.put("id", id);
            }
            case "ADMIN_ROLE" -> {
                query.put("tab", "roles");
                query.put("role_id", id);
            }
            default -> query.put("id", id);
        }
        return query;
    }

    private List<OperationLogDetailVO.Related> related(OperationLog row) {
        List<OperationLogDetailVO.Related> related = new ArrayList<>();
        if (row.getBizType() == null || row.getBizId() == null) {
            return related;
        }
        List<OperationLog> rows = operationLogQueryMapper.selectRelated(row.getBizType(), row.getBizId(),
                row.getId(), RELATED_LIMIT);
        for (OperationLog item : rows) {
            related.add(OperationLogDetailVO.Related.builder()
                    .time(item.getCreatedAt())
                    .desc(AdminLogTexts.opTypeText(item.getOpType()) + " · " + AdminLogTexts.opActionText(item.getOpAction()))
                    .source(item.getOperatorType())
                    .sourceText(AdminLogTexts.sourceText(item.getOperatorType()))
                    .statusText(AdminLogTexts.resultText(item.getResult()))
                    .build());
        }
        return related;
    }

    private static OperationLogItemVO toItem(OperationLog row) {
        return OperationLogItemVO.builder()
                .logId(row.getId())
                .createdAt(row.getCreatedAt())
                .operatorName(row.getOperatorName())
                .operatorRole(row.getOperatorRole())
                .operatorRoleText(AdminLogTexts.roleText(row.getOperatorRole()))
                .opType(row.getOpType())
                .opTypeText(AdminLogTexts.opTypeText(row.getOpType()))
                .opAction(row.getOpAction())
                .opActionText(AdminLogTexts.opActionText(row.getOpAction()))
                .result(row.getResult())
                .targetName(row.getTargetName())
                .reason(row.getReason())
                .build();
    }

    private static void validateSpan(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, MSG_DATE_ORDER);
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > MAX_SPAN_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID, MSG_DATE_SPAN);
        }
    }

    /** 当前查看人的可见范围；超管看到全部 ADMIN / SYSTEM 日志 */
    private static Visibility visibility() {
        AdminContext.LoginAdmin admin = AdminContext.get();
        if (admin == null) {
            return new Visibility(false, Set.of());
        }
        if (admin.isSuperAdmin()) {
            return new Visibility(true, Set.of());
        }
        Set<String> allowed = new LinkedHashSet<>();
        if (admin.hasPermission("WA-R01:HANDLE")) {
            allowed.add("FARM_AUDIT");
        }
        if (admin.hasPermission("WA-R02:HANDLE")) {
            allowed.add("FARM_CHANGE");
        }
        if (admin.hasPermission("WA-R03:HANDLE")) {
            allowed.add("CHANGE_REQUEST");
        }
        if (admin.hasPermission("WA-R04:HANDLE")) {
            allowed.add("ACCOUNT_RECOVERY");
        }
        addWhen(allowed, admin, "WA-C01:HANDLE", "NOTICE");
        addWhen(allowed, admin, "WA-C02:HANDLE", "MARKET_PRICE");
        addWhen(allowed, admin, "WA-C03:HANDLE", "COURSE");
        addWhen(allowed, admin, "WA-C04:HANDLE", "KNOWLEDGE");
        addWhen(allowed, admin, "WA-C05:HANDLE", "AI_CORRECTION");
        boolean baseData = false;
        for (String permission : List.of("WA-D01:HANDLE", "WA-D02:HANDLE", "WA-D03:HANDLE",
                "WA-D04:HANDLE", "WA-D05:HANDLE")) {
            if (admin.hasPermission(permission)) {
                baseData = true;
                break;
            }
        }
        if (baseData) {
            allowed.addAll(AdminLogTexts.BASE_DATA_OP_TYPES);
        }
        if (admin.hasPermission("WA-S03:HANDLE")) {
            allowed.addAll(AdminLogTexts.USER_OP_TYPES);
        }
        if (admin.hasPermission("WA-S04:HANDLE")) {
            allowed.addAll(AdminLogTexts.FARM_FREEZE_OP_TYPES);
        }
        addWhen(allowed, admin, "WA-S05:HANDLE", "LOGIN");
        addWhen(allowed, admin, "WA-S05:HANDLE", "ADMIN_RBAC");
        // 导出日志跟随被导出页面的 HANDLE；任一管理端页面有处理权限即可见 EXPORT
        if (admin.permissions() != null && admin.permissions().stream().anyMatch(code -> code.endsWith(":HANDLE"))) {
            allowed.add("EXPORT");
        }
        return new Visibility(false, allowed);
    }

    private static void addWhen(Set<String> allowed, AdminContext.LoginAdmin admin,
                                String permission, String opType) {
        if (admin.hasPermission(permission)) {
            allowed.add(opType);
        }
    }

    private static boolean visible(OperationLog row, Long viewerId, Visibility visibility) {
        if (!"ADMIN".equals(row.getOperatorType()) && !"SYSTEM".equals(row.getOperatorType())) {
            return false;
        }
        if (viewerId.equals(row.getOperatorId())) {
            return true;
        }
        return visibility.allVisible() || visibility.allowedOpTypes().contains(row.getOpType());
    }

    /**
     * 解析 before_json / after_json：字段编码 → {name 中文名, display 显示文本}。
     * 兼容直接写显示文字的简单结构；输出按 after、before 的并集顺序。
     */
    private static List<OperationLogDetailVO.Change> changes(String beforeJson, String afterJson) {
        Map<String, FieldDisplay> before = parse(beforeJson);
        Map<String, FieldDisplay> after = parse(afterJson);
        Set<String> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        List<OperationLogDetailVO.Change> changes = new ArrayList<>();
        for (String key : keys) {
            FieldDisplay b = before.get(key);
            FieldDisplay a = after.get(key);
            String name = a != null && a.name() != null ? a.name() : b == null ? null : b.name();
            String beforeText = b == null ? null : b.display;
            String afterText = a == null ? null : a.display;
            if (name == null && beforeText == null && afterText == null) {
                continue;
            }
            if (b != null && a != null && Objects.equals(beforeText, afterText)) {
                // 早先的“修改”日志整份写快照：前后相同的字段不是修改内容，不列出
                continue;
            }
            changes.add(OperationLogDetailVO.Change.builder()
                    .fieldName(name == null ? key : name)
                    .before(beforeText)
                    .after(afterText)
                    .build());
        }
        return changes;
    }

    private static Map<String, FieldDisplay> parse(String json) {
        Map<String, FieldDisplay> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        JsonNode root;
        try {
            root = CacheJson.mapper().readTree(json);
        } catch (RuntimeException e) {
            log.debug("管理端日志变更 JSON 解析失败", e);
            return result;
        }
        if (root == null || !root.isObject()) {
            return result;
        }
        Iterator<String> names = root.propertyNames().iterator();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                // 直接写编码的简单结构（例如 {"status":"ON_SHELF"}）：字段名、状态编码都转中文
                result.put(key, new FieldDisplay(AdminLogTexts.fieldText(key),
                        AdminLogTexts.valueText(value.asString())));
            } else if (value.isNumber()) {
                result.put(key, new FieldDisplay(AdminLogTexts.fieldText(key), value.asString()));
            } else if (value.isBoolean()) {
                result.put(key, new FieldDisplay(AdminLogTexts.fieldText(key), value.asBoolean() ? "是" : "否"));
            } else if (value.isArray()) {
                List<String> parts = new ArrayList<>();
                for (JsonNode element : value) {
                    if (element != null && element.isValueNode() && !element.isNull()) {
                        parts.add(AdminLogTexts.valueText(element.asString()));
                    }
                }
                if (!parts.isEmpty()) {
                    result.put(key, new FieldDisplay(AdminLogTexts.fieldText(key), String.join("、", parts)));
                }
            } else {
                result.put(key, new FieldDisplay(text(value, "name"), text(value, "display")));
            }
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private record FieldDisplay(String name, String display) {
    }

    private record Visibility(boolean allVisible, Set<String> allowedOpTypes) {
    }
}
