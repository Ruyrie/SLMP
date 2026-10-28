package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次性临时密码（ADM-S24）。平台没有通知类短信模板，任何环境都只在本次响应里返回给操作人，
 * 页面只显示一次，由操作人线下告知；不写日志。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempPasswordVO {
    private String tempPassword;
    private java.time.LocalDateTime tempPasswordExpireAt;
}
