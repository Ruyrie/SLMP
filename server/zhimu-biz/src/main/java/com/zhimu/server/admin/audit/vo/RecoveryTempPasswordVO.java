package com.zhimu.server.admin.audit.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R18 重新生成临时密码的响应。临时密码明文只在这次响应里返回一次，由管理员线下告知用户；
 * 不落库、不写日志。
 */
@Data
@Builder
public class RecoveryTempPasswordVO {

    private Long id;
    /** 账号当前登录手机号（打码） */
    private String phone;
    private String userName;
    private String tempPassword;
    private LocalDateTime tempPasswordExpireAt;
    private LocalDateTime regeneratedAt;
}
