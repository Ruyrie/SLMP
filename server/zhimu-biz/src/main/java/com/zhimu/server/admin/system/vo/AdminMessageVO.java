package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/** ADM-S40 铃铛消息。route_params 原样输出为对象（可能是 null）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMessageVO {
    private Long messageId;
    private String msgType;
    private String msgTypeText;
    private String level;
    private String title;
    private String summary;
    private String bizType;
    private Long bizId;
    private String routePage;
    private Map<String, Object> routeParams;
    private Boolean read;
    private LocalDateTime createdAt;
}
