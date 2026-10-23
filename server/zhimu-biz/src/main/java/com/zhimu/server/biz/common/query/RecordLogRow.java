package com.zhimu.server.biz.common.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 修改记录查询行（t_operation_log 左连 t_change_request）。
 */
@Data
public class RecordLogRow {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String opAction;
    private String reason;
    private String beforeJson;
    private String afterJson;
    private Long farmId;
    private Long changeRequestId;
    private String changeRequestNo;
    private LocalDateTime createdAt;
}
