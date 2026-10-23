package com.zhimu.server.admin.system.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一条管理端操作留痕的参数。写入方不填 operator_*、client_type、ip 等，由框架按
 * AdminContext 与 RequestContext 自动补齐；before / after 为“字段编码 → {name, display}”对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLogCommand {

    private String opType;
    private String opAction;
    private String bizType;
    private Long bizId;
    private String targetName;
    private Long farmId;
    private String searchText;
    private String reason;
    private String extraText;
    private String notifyText;
    private Object before;
    private Object after;
    private String result;
}
