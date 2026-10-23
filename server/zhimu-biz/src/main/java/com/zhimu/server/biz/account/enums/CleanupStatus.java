package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 注销清理状态（t_account_cancel.cleanup_status） */
@Getter
public enum CleanupStatus {
    PENDING("待清理"),
    RUNNING("清理中"),
    WAITING("等待保留期"),
    DONE("已完成"),
    FAILED("清理失败");

    private final String text;

    CleanupStatus(String text) {
        this.text = text;
    }
}
