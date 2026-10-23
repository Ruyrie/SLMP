package com.zhimu.server.framework.oplog;

import lombok.Builder;
import lombok.Data;

/**
 * 一条操作留痕（对应 t_operation_log）。operator_* 与 client_type、ip、user_agent 未填时由记录器按当前上下文补齐。
 */
@Data
@Builder
public class OperationLogEntry {
    /** ADMIN / USER / SYSTEM */
    private String operatorType;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String opType;
    private String opAction;
    /** SUCCESS / FAIL */
    @Builder.Default
    private String result = "SUCCESS";
    private String bizType;
    private Long bizId;
    private String targetName;
    private Long farmId;
    private String searchText;
    private String reason;
    private String extraText;
    private String notifyText;
    /** 变更前后的字段值（任意对象，序列化为 JSON） */
    private Object before;
    private Object after;
    private Long changeRequestId;
}