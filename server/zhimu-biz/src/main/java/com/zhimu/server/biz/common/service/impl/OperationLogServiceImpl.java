package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.OperationLogQueryService;
import com.zhimu.server.biz.common.change.ChangeBizTypes;
import com.zhimu.server.biz.common.mapper.OperationLogMapper;
import com.zhimu.server.biz.common.query.RecordLogRow;
import com.zhimu.server.biz.common.vo.RecordLogVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.error.Asserts;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.vo.UserBriefVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 修改记录查询（API-U14）：不分页，data 直接是数组，最多返回最近 200 条；只取 operator_type = USER 的日志。
 * changes.before / after 为带单位的显示文字，field_name 为中文；op_action 固定 UPDATE / VOID / DELETE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogQueryService {

    /** U14 最多返回最近 200 条 */
    public static final int MAX_LIMIT = 200;

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private final OperationLogMapper operationLogMapper;
    private final ObjectProvider<AccountQueryService> accountQueryService;
    private final FarmMemberCache farmMemberCache;

    @Override
    public List<RecordLogVO> listByBiz(BizType bizType, Long bizId, int limit) {
        Asserts.notNull(bizType, ErrorCode.PARAM_INVALID);
        Asserts.notNull(bizId, ErrorCode.PARAM_INVALID);
        Long userId = UserContext.requireUserId();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<RecordLogRow> rows = TenantSkip.call(() ->
                operationLogMapper.selectUserLogs(bizType.name(), bizId, effectiveLimit));
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        checkFarmAccess(rows, userId, bizType);
        Map<Long, String> names = loadNames(rows);
        List<RecordLogVO> result = new ArrayList<>(rows.size());
        for (RecordLogRow row : rows) {
            RecordLogVO vo = toVo(row, names, bizType);
            // 旧版栏舍保存会记入未改变的字段和缺失字段；没有可信差异时不展示虚假的修改。
            if (bizType == BizType.PEN && "UPDATE".equals(row.getOpAction()) && vo.getChanges().isEmpty()) {
                continue;
            }
            result.add(vo);
        }
        return result;
    }

    /** 记录所属养殖场必须是本人可查看的养殖场；员工栏舍范围由各模块的 BizRecordAccessor 判断，本批不细化 */
    private void checkFarmAccess(List<RecordLogRow> rows, Long userId, BizType bizType) {
        Set<Long> farmIds = new LinkedHashSet<>();
        for (RecordLogRow row : rows) {
            if (row.getFarmId() != null) {
                farmIds.add(row.getFarmId());
            }
        }
        for (Long farmId : farmIds) {
            var member = farmMemberCache.get(farmId, userId);
            if (member == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            if (bizType == BizType.FINANCE_RECORD && !member.isManagerView()) {
                throw new BizException(ErrorCode.ROLE_FORBIDDEN);
            }
        }
    }

    private Map<Long, String> loadNames(List<RecordLogRow> rows) {
        Map<Long, String> names = new LinkedHashMap<>();
        Set<Long> missing = new LinkedHashSet<>();
        for (RecordLogRow row : rows) {
            if (row.getOperatorId() != null) {
                if (row.getOperatorName() == null || row.getOperatorName().isBlank()) {
                    missing.add(row.getOperatorId());
                } else {
                    names.put(row.getOperatorId(), row.getOperatorName());
                }
            }
        }
        AccountQueryService service = accountQueryService.getIfAvailable();
        if (service != null && !missing.isEmpty()) {
            try {
                Map<Long, AccountBrief> briefs = service.listBriefs(missing);
                if (briefs != null) {
                    briefs.forEach((id, brief) -> {
                        if (brief != null && brief.name() != null) {
                            names.put(id, brief.name());
                        }
                    });
                }
            } catch (RuntimeException e) {
                log.debug("修改记录补操作人姓名失败 {}", missing, e);
            }
        }
        return names;
    }

    private static RecordLogVO toVo(RecordLogRow row, Map<Long, String> names, BizType bizType) {
        String name = names.get(row.getOperatorId());
        String role = row.getOperatorRole();
        UserBriefVO operator = UserBriefVO.builder()
                .userId(row.getOperatorId())
                .name(name)
                .role(role)
                .roleText(CodeEnum.textOf(FarmRole.class, role))
                .build();
        return RecordLogVO.builder()
                .id(row.getId())
                .operator(operator)
                .opAction(row.getOpAction())
                .opActionText(actionText(row.getOpAction(), bizType))
                .changes(changes(row.getBeforeJson(), row.getAfterJson(), bizType, row.getOpAction()))
                .reason(row.getReason())
                .changeRequestNo(row.getChangeRequestNo())
                .createdAt(row.getCreatedAt())
                .build();
    }

    /**
     * 留痕 JSON：字段编码 → {name 中文名, display 显示文本}。
     * 兼容两种写法：直接是字段 map，或放在 before_json / after_json 统一 schema 的 before / after 节点。
     */
    static List<RecordLogVO.Change> changes(String beforeJson, String afterJson) {
        return changes(beforeJson, afterJson, null, null);
    }

    private static List<RecordLogVO.Change> changes(String beforeJson, String afterJson,
                                                   BizType bizType, String action) {
        Map<String, FieldDisplay> before = parse(beforeJson, "before", bizType);
        Map<String, FieldDisplay> after = parse(afterJson, "after", bizType);
        Set<String> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        List<RecordLogVO.Change> changes = new ArrayList<>(keys.size());
        for (String key : keys) {
            FieldDisplay b = before.get(key);
            FieldDisplay a = after.get(key);
            // 旧 PEN UPDATE 使用不完整的标量快照。字段未记录不等于清空；显式 null 仍保留。
            if (bizType == BizType.PEN && "UPDATE".equals(action)
                    && ((a == null && b != null && b.scalar()) || (b == null && a != null && a.scalar()))) {
                continue;
            }
            String fieldName = a != null && a.name() != null ? a.name() : b == null ? null : b.name();
            String beforeText = b == null ? null : b.display;
            String afterText = a == null ? null : a.display;
            // 旧用药 UPDATE 仅写了末次日期和修改后的用量，缺失原值不代表原值为空。
            if (bizType == BizType.MEDICATION_RECORD && "UPDATE".equals(action)) {
                if (b == null && a != null) beforeText = "未记录";
                if (a == null && b != null) afterText = "未记录";
            }
            if (Objects.equals(beforeText, afterText)) {
                continue;
            }
            changes.add(RecordLogVO.Change.builder()
                    .fieldName(fieldName == null ? key : fieldName)
                    .before(beforeText)
                    .after(afterText)
                    .build());
        }
        return changes;
    }

    private static Map<String, FieldDisplay> parse(String json, String section, BizType bizType) {
        Map<String, FieldDisplay> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (RuntimeException e) {
            log.debug("留痕 JSON 解析失败", e);
            return result;
        }
        if (root == null || !root.isObject()) {
            return result;
        }
        JsonNode node = root;
        if (root.has("compare_keys") || root.has("record_card")) {
            node = root.path(section);
        }
        if (!node.isObject()) {
            return result;
        }
        java.util.Iterator<String> names = node.propertyNames().iterator();
        while (names.hasNext()) {
            String key = names.next();
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                result.put(key, new FieldDisplay(fieldName(key, bizType), null, true));
                continue;
            }
            if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                // 兼容直接保存文字/数字的早期日志，数字不能按对象读取而丢失。
                String display = value.isTextual() ? value.asString() : value.toString();
                result.put(key, new FieldDisplay(fieldName(key, bizType),
                        scalarDisplay(key, display, bizType), true));
                continue;
            }
            result.put(key, new FieldDisplay(text(value, "name"), text(value, "display"), false));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String fieldName(String key, BizType bizType) {
        if (bizType == BizType.MEDICATION_RECORD) {
            return switch (key) {
                case "drug_name" -> "药品名称";
                case "lot_no" -> "药品批号";
                case "used_qty" -> "实际用量";
                case "first_date" -> "首次用药日期";
                case "last_date" -> "末次用药日期";
                case "dose" -> "用药剂量";
                case "course_days" -> "疗程";
                case "withdrawal_days" -> "休药天数";
                case "withdrawal_end_date" -> "休药结束日期";
                case "route_code" -> "给药途径";
                case "reason" -> "用药原因";
                case "over_dose_note" -> "超剂量说明";
                case "usage_note" -> "适用品类说明";
                case "executor_id" -> "执行人";
                case "report_id" -> "关联异常上报";
                case "deleted" -> "记录状态";
                default -> key;
            };
        }
        if (bizType != BizType.PEN) {
            return key;
        }
        return switch (key) {
            case "pen_name" -> "栏舍名称";
            case "pen_code" -> "栏舍编号";
            case "pen_type" -> "栏舍类型";
            case "capacity" -> "容量";
            case "category_id" -> "适用品类";
            case "status" -> "栏舍状态";
            default -> key;
        };
    }

    private static String scalarDisplay(String key, String display, BizType bizType) {
        if (bizType == BizType.MEDICATION_RECORD) {
            if ("deleted".equals(key)) return "true".equals(display) ? "已删除" : "有效";
            if ("course_days".equals(key) || "withdrawal_days".equals(key)) return display + " 天";
        }
        if (bizType != BizType.PEN) {
            return display;
        }
        if ("pen_type".equals(key)) {
            return switch (display) {
                case "NURSERY" -> "保育舍";
                case "FATTENING" -> "育肥舍";
                case "SOW" -> "母畜舍";
                case "FARROWING" -> "产房";
                case "BROODING" -> "育雏舍";
                case "BROILER" -> "肉鸡舍";
                case "BREEDER" -> "种鸡舍";
                default -> display;
            };
        }
        if ("status".equals(key)) {
            return switch (display) {
                case "ENABLED" -> "启用";
                case "DISABLED" -> "停用";
                default -> display;
            };
        }
        return display;
    }

    private record FieldDisplay(String name, String display, boolean scalar) {
    }

    private static String actionText(String action, BizType bizType) {
        if (bizType == BizType.MEDICATION_RECORD && action != null) {
            return switch (action) {
                case "CREATE" -> "登记用药";
                case "UPDATE" -> "修改用药记录";
                case "DELETE" -> "删除用药记录";
                default -> ChangeBizTypes.actionText(action);
            };
        }
        return ChangeBizTypes.actionText(action);
    }
}
