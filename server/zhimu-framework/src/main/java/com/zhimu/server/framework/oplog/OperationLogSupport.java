package com.zhimu.server.framework.oplog;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.OperatorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 操作留痕公共逻辑，供 {@link OpLogAspect} 与 {@link OperationLogRecorder} 的实现方复用。
 */
public final class OperationLogSupport {

    /** 没有 OperationLogRecorder 实现时，留痕以 JSON 写到这个 logger（logback 单独输出到 logs/oplog.log） */
    public static final String LOGGER_NAME = "OPLOG";

    private static final Logger OPLOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Logger log = LoggerFactory.getLogger(OperationLogSupport.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private OperationLogSupport() {
    }

    /**
     * 按当前上下文补齐未填写的字段（已填写的保持不变）：
     * <ul>
     *     <li>有 AdminContext：ADMIN，操作人 ID、姓名、角色编码（多个用逗号拼接）</li>
     *     <li>否则有 UserContext：USER，操作人 ID，角色取 FarmContext.role（姓名上下文中没有，由记录器按需查询）</li>
     *     <li>都没有：SYSTEM</li>
     *     <li>farm_id 未填时取 FarmContext 的当前养殖场</li>
     * </ul>
     * client_type、ip、user_agent 不在 {@link OperationLogEntry} 中，记录器写库时用 {@link #requestInfo()} 取。
     */
    public static OperationLogEntry fillContext(OperationLogEntry entry) {
        AdminContext.LoginAdmin admin = AdminContext.get();
        UserContext.LoginUser user = UserContext.get();
        FarmContext.FarmMember farm = FarmContext.get();

        if (entry.getOperatorType() == null) {
            OperatorType type = admin != null ? OperatorType.ADMIN
                    : user != null ? OperatorType.USER : OperatorType.SYSTEM;
            entry.setOperatorType(type.name());
        }
        if (OperatorType.ADMIN.name().equals(entry.getOperatorType()) && admin != null) {
            if (entry.getOperatorId() == null) {
                entry.setOperatorId(admin.adminId());
            }
            if (entry.getOperatorName() == null) {
                entry.setOperatorName(admin.name());
            }
            if (entry.getOperatorRole() == null && admin.roleCodes() != null && !admin.roleCodes().isEmpty()) {
                entry.setOperatorRole(joinRoles(admin.roleCodes()));
            }
        } else if (OperatorType.USER.name().equals(entry.getOperatorType()) && user != null) {
            if (entry.getOperatorId() == null) {
                entry.setOperatorId(user.userId());
            }
            if (entry.getOperatorRole() == null && farm != null && farm.role() != null) {
                entry.setOperatorRole(farm.role().name());
            }
        }
        if (entry.getFarmId() == null && farm != null) {
            entry.setFarmId(farm.farmId());
        }
        return entry;
    }

    /** 当前请求来源（client_type、ip、user_agent）；非请求线程（定时任务等）为 null */
    public static RequestContext.Info requestInfo() {
        return RequestContext.get();
    }

    /** 把留痕以一行 JSON 写到 OPLOG 日志（含请求来源与 trace_id） */
    public static void writeToLog(OperationLogEntry entry) {
        try {
            OPLOG.info(JSON.writeValueAsString(toLogMap(entry)));
        } catch (RuntimeException e) {
            log.warn("留痕序列化失败，按文本写日志", e);
            OPLOG.info(String.valueOf(entry));
        }
    }

    static Map<String, Object> toLogMap(OperationLogEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "operator_type", entry.getOperatorType());
        putIfNotNull(map, "operator_id", entry.getOperatorId());
        putIfNotNull(map, "operator_name", entry.getOperatorName());
        putIfNotNull(map, "operator_role", entry.getOperatorRole());
        putIfNotNull(map, "op_type", entry.getOpType());
        putIfNotNull(map, "op_action", entry.getOpAction());
        putIfNotNull(map, "result", entry.getResult());
        putIfNotNull(map, "biz_type", entry.getBizType());
        putIfNotNull(map, "biz_id", entry.getBizId());
        putIfNotNull(map, "target_name", entry.getTargetName());
        putIfNotNull(map, "farm_id", entry.getFarmId());
        putIfNotNull(map, "search_text", entry.getSearchText());
        putIfNotNull(map, "reason", entry.getReason());
        putIfNotNull(map, "extra_text", entry.getExtraText());
        putIfNotNull(map, "notify_text", entry.getNotifyText());
        putIfNotNull(map, "before", entry.getBefore());
        putIfNotNull(map, "after", entry.getAfter());
        putIfNotNull(map, "change_request_id", entry.getChangeRequestId());
        RequestContext.Info request = RequestContext.get();
        if (request != null) {
            putIfNotNull(map, "client_type", request.clientType() == null ? null : request.clientType().name());
            putIfNotNull(map, "ip", request.ip());
            putIfNotNull(map, "user_agent", request.userAgent());
        }
        putIfNotNull(map, "trace_id", TraceContext.get());
        return map;
    }

    private static String joinRoles(Set<String> roleCodes) {
        return String.join(",", new TreeSet<>(roleCodes));
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
