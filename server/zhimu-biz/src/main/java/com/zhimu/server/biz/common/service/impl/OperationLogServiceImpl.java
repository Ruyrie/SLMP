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
        checkFarmAccess(rows, userId);
        Map<Long, String> names = loadNames(rows);
        List<RecordLogVO> result = new ArrayList<>(rows.size());
        for (RecordLogRow row : rows) {
            result.add(toVo(row, names));
        }
        return result;
    }

    /** 记录所属养殖场必须是本人可查看的养殖场；员工栏舍范围由各模块的 BizRecordAccessor 判断，本批不细化 */
    private void checkFarmAccess(List<RecordLogRow> rows, Long userId) {
        Set<Long> farmIds = new LinkedHashSet<>();
        for (RecordLogRow row : rows) {
            if (row.getFarmId() != null) {
                farmIds.add(row.getFarmId());
            }
        }
        for (Long farmId : farmIds) {
            if (farmMemberCache.get(farmId, userId) == null) {
                throw new BizException(ErrorCode.NOT_FOUND);
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

    private static RecordLogVO toVo(RecordLogRow row, Map<Long, String> names) {
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
                .opActionText(ChangeBizTypes.actionText(row.getOpAction()))
                .changes(changes(row.getBeforeJson(), row.getAfterJson()))
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
        Map<String, FieldDisplay> before = parse(beforeJson, "before");
        Map<String, FieldDisplay> after = parse(afterJson, "after");
        Set<String> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        List<RecordLogVO.Change> changes = new ArrayList<>(keys.size());
        for (String key : keys) {
            FieldDisplay b = before.get(key);
            FieldDisplay a = after.get(key);
            String fieldName = a != null && a.name() != null ? a.name() : b == null ? null : b.name();
            String beforeText = b == null ? null : b.display;
            String afterText = a == null ? null : a.display;
            if (fieldName == null && beforeText == null && afterText == null) {
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

    private static Map<String, FieldDisplay> parse(String json, String section) {
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
                continue;
            }
            if (value.isTextual()) {
                // 兼容“直接写显示文字”的简单写法
                result.put(key, new FieldDisplay(key, value.asString()));
                continue;
            }
            result.put(key, new FieldDisplay(text(value, "name"), text(value, "display")));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private record FieldDisplay(String name, String display) {
    }
}
