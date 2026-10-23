package com.zhimu.server.biz.account.enums;

import lombok.Getter;

/** 注销阻止原因（CancellationPreviewVO.block_reasons） */
@Getter
public enum CancelBlockReason {
    RECOVERED_WITHIN_7_DAYS("找回后 7 天内"),
    OWNED_FARM_ACTIVE("名下有正常养殖场"),
    OWNED_FARM_FROZEN("名下有冻结养殖场");

    private final String text;

    CancelBlockReason(String text) {
        this.text = text;
    }
}
