package com.zhimu.server.biz.account.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 模块01 可配置项（zhimu.account）：默认值即文档约定值；修改协议版本时同时改 agreement-version 与地址。
 * 只写默认值，不修改 application*.yml（第 1 批文件归属限制）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zhimu.account")
public class AccountProperties {

    /** 协议组合版本，A21 返回、A03 校验 */
    private String agreementVersion = "2026-10-01";

    /** 协议地址前缀，完整地址为 {baseUrl}/user-{version}.html、{baseUrl}/privacy-{version}.html */
    private String agreementBaseUrl = "https://www.zhimutong.example.com/agreements";

    private String customerServicePhone = "400-123-4567";

    private int smsCooldownSeconds = 60;

    private int smsExpireSeconds = 300;

    private int smsDailyLimit = 10;

    private int smsMaxAttempts = 5;

    private int passwordMaxFailures = 5;

    private Duration passwordLockTtl = Duration.ofMinutes(30);

    private int recoveryMaxFailures = 5;

    private Duration recoveryWindow = Duration.ofHours(24);

    private Duration recoveryLockTtl = Duration.ofHours(24);

    private Duration tempPasswordTtl = Duration.ofHours(24);

    private Duration phoneRelease = Duration.ofDays(30);

    private Duration farmRetention = Duration.ofDays(180);

    /** 找回后多少天内不能注销 */
    private Duration cancelRestrict = Duration.ofDays(7);
}
