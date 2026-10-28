package com.zhimu.server.admin.system.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ADM-S22 新增管理员结果：admin_id 与一次性 temp_password（平台没有通知短信，页面只显示一次，由操作人线下告知；不写日志）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreatedVO {
    private Long adminId;
    private String tempPassword;
    private java.time.LocalDateTime tempPasswordExpireAt;
}
