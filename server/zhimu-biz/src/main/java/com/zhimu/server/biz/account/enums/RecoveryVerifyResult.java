package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 恢复码校验结果（t_recovery_verify_log.result） */
@Getter
public enum RecoveryVerifyResult {
    CODE_CORRECT("正确"),
    CODE_WRONG("不正确"),
    LOCKED("已锁定"),
    PHONE_NOT_REGISTERED("原手机号未注册");

    private final String text;

    RecoveryVerifyResult(String text) {
        this.text = text;
    }
}
