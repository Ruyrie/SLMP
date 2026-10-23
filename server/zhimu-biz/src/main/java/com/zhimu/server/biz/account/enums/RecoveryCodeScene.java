package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 恢复码场景（t_recovery_code.scene） */
@Getter
public enum RecoveryCodeScene {
    REGISTER("注册"),
    RECOVERY("找回"),
    REGENERATE("重新生成");

    private final String text;

    RecoveryCodeScene(String text) {
        this.text = text;
    }
}
