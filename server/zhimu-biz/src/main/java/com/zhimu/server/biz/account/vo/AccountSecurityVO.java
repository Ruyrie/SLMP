package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A15 的 security：登录手机号、密码与恢复码时间、注销限制 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSecurityVO {
    private String phoneMasked;
    private String passwordUpdatedAt;
    private String recoveryCodeGeneratedAt;
    private Boolean cancelRestricted;
    private String cancelAvailableDate;
}
