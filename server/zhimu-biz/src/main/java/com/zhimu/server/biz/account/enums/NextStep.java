package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 强制步骤（AuthTokenVO.next_step、SessionVO.next_step） */
@Getter
public enum NextStep {
    NONE("无需操作"),
    RECOVERY_CHANGE_PASSWORD("请先修改密码"),
    SAVE_RECOVERY_CODE("请保存恢复码");

    private final String text;

    NextStep(String text) {
        this.text = text;
    }
}
