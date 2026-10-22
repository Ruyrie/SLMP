package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 登录、注册、刷新令牌（API-A03–A06、A11、A17）的返回。
 */
public class AuthTokenVO {

    public static final String NEXT_NONE = "NONE";
    public static final String NEXT_SAVE_RECOVERY_CODE = "SAVE_RECOVERY_CODE";
    public static final String NEXT_RECOVERY_CHANGE_PASSWORD = "RECOVERY_CHANGE_PASSWORD";

    @Nullable
    private String accessToken;
    /** Bearer */
    @Nullable
    private String tokenType;
    /** 秒，7200 */
    private int expiresIn;
    /** 秒，2592000 */
    private int refreshExpiresIn;
    /** App 必有 */
    @Nullable
    private String refreshToken;
    /** NONE / SAVE_RECOVERY_CODE / RECOVERY_CHANGE_PASSWORD */
    @Nullable
    private String nextStep;
    @Nullable
    private String nextStepText;
    @Nullable
    private AccountUserVO user;
    /** 没有养殖场时不输出 */
    @Nullable
    private Long lastFarmId;
    @Nullable
    private RecoveryInfoVO recoveryInfo;
    /** 仅 A03、A11 */
    @Nullable
    private RecoveryCodeVO recoveryCode;

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    @Nullable
    public String getTokenType() {
        return tokenType;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public int getRefreshExpiresIn() {
        return refreshExpiresIn;
    }

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    @Nullable
    public String getNextStep() {
        return nextStep;
    }

    @Nullable
    public String getNextStepText() {
        return nextStepText;
    }

    @Nullable
    public AccountUserVO getUser() {
        return user;
    }

    @Nullable
    public Long getLastFarmId() {
        return lastFarmId;
    }

    @Nullable
    public RecoveryInfoVO getRecoveryInfo() {
        return recoveryInfo;
    }

    @Nullable
    public RecoveryCodeVO getRecoveryCode() {
        return recoveryCode;
    }
}
