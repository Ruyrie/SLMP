package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录、注册、刷新、找回后改密、修改密码的返回 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenVO {
    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    private Integer refreshExpiresIn;
    /** X-Client 为 WEB 时不输出，改用 Cookie */
    private String refreshToken;
    private String nextStep;
    private String nextStepText;
    private AccountUserVO user;
    private Long lastFarmId;
    private RecoveryInfoVO recoveryInfo;
    /** 仅 A03 首次注册、A11 返回 */
    private RecoveryCodeVO recoveryCode;
}
