package com.zhimu.server.admin.system.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 一条管理端站内消息。send 必须指定 adminId；sendToPermission 忽略 adminId，
 * 按权限码发给全部正常管理员。merge_key 非空时同一接收人同一键只保留一条。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMessageCommand {

    private Long adminId;
    private String msgType;
    /** NORMAL / IMPORTANT */
    private String level;
    private String title;
    private String summary;
    private String bizType;
    private Long bizId;
    private String routePage;
    private Map<String, Object> routeParams;
    private String mergeKey;
}
