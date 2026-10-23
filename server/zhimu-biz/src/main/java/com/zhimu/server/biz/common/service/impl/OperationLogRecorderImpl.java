package com.zhimu.server.biz.common.service.impl;

import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.biz.common.mapper.OperationLogMapper;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.OperatorType;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.oplog.OperationLogSupport;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 操作留痕写库（t_operation_log，公共能力 biz.common 实现）。
 * <p>
 * 按当前上下文补齐 operator_*、client_type、ip、user_agent；ip_region 通过 {@link IpRegionResolver} 解析；
 * before / after 序列化为“字段编码 → {name, display}”形式的 JSON。直接插入，与调用方业务在同一事务内提交。
 * 该表没有 updated_at / deleted / version，使用独立实体，不继承 BaseEntity / AuditEntity。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogRecorderImpl implements OperationLogRecorder {

    private static final JsonMapper JSON = ZhimuJackson.newPlainMapper();

    private final OperationLogMapper operationLogMapper;
    private final ObjectProvider<AccountQueryService> accountQueryService;
    private final ObjectProvider<IpRegionResolver> ipRegionResolver;

    @Override
    public void record(OperationLogEntry entry) {
        if (entry == null) {
            return;
        }
        OperationLogSupport.fillContext(entry);
        OperationLog logRow = new OperationLog();
        logRow.setOperatorType(entry.getOperatorType());
        logRow.setOperatorId(entry.getOperatorId());
        logRow.setOperatorName(truncate(resolveOperatorName(entry), 32));
        logRow.setOperatorRole(truncate(entry.getOperatorRole(), 100));
        logRow.setOpType(truncate(entry.getOpType(), 20));
        logRow.setOpAction(truncate(entry.getOpAction(), 20));
        logRow.setResult(entry.getResult() == null ? "SUCCESS" : truncate(entry.getResult(), 20));
        logRow.setBizType(truncate(entry.getBizType(), 30));
        logRow.setBizId(entry.getBizId());
        logRow.setTargetName(truncate(entry.getTargetName(), 200));
        logRow.setFarmId(entry.getFarmId());
        logRow.setSearchText(truncate(entry.getSearchText(), 255));
        logRow.setReason(truncate(entry.getReason(), 500));
        logRow.setExtraText(truncate(entry.getExtraText(), 500));
        logRow.setNotifyText(truncate(entry.getNotifyText(), 500));
        logRow.setBeforeJson(toJson(entry.getBefore()));
        logRow.setAfterJson(toJson(entry.getAfter()));
        logRow.setChangeRequestId(entry.getChangeRequestId());
        logRow.setClientType(truncate(entry.getClientType(), 20));
        logRow.setUserAgent(truncate(entry.getUserAgent(), 500));
        logRow.setIp(truncate(entry.getIp(), 45));
        logRow.setIpRegion(truncate(resolveIpRegion(entry), 64));
        operationLogMapper.insert(logRow);
    }

    /** 用户端日志没有姓名快照时按账号查询补齐；查不到时保留空（不写手机号等敏感信息） */
    private String resolveOperatorName(OperationLogEntry entry) {
        if (entry.getOperatorName() != null && !entry.getOperatorName().isBlank()) {
            return entry.getOperatorName();
        }
        if (!OperatorType.USER.name().equals(entry.getOperatorType())) {
            return null;
        }
        Long userId = entry.getOperatorId() != null ? entry.getOperatorId() : UserContext.userId();
        if (userId == null) {
            return null;
        }
        AccountQueryService service = accountQueryService.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            AccountBrief brief = service.getBrief(userId);
            return brief == null ? null : brief.name();
        } catch (RuntimeException e) {
            log.debug("留痕补姓名失败 user_id={}", userId, e);
            return null;
        }
    }

    private String resolveIpRegion(OperationLogEntry entry) {
        if (entry.getIpRegion() != null && !entry.getIpRegion().isBlank()) {
            return entry.getIpRegion();
        }
        String ip = entry.getIp();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        IpRegionResolver resolver = ipRegionResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.resolve(ip);
        } catch (RuntimeException e) {
            log.debug("IP 属地解析失败 ip={}", ip, e);
            return null;
        }
    }

    /** 已经是 JSON 文本时不再二次序列化 */
    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return text;
            }
        }
        return JSON.writeValueAsString(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
