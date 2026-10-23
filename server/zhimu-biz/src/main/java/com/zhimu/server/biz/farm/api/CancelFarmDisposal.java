package com.zhimu.server.biz.farm.api;

import com.zhimu.server.common.enums.CodeEnum;
import lombok.Getter;

/** 注销时本人创建的养殖场的处置方式（CANCEL_FARM_DISPOSAL）；中文按 UI 文档（2026-10-23 用户确认） */
@Getter
public enum CancelFarmDisposal implements CodeEnum {
    TRANSFER_OR_DISABLE("仍在使用，须先转让或停用"),
    RETAIN_180_DAYS("数据保留 180 天后清理"),
    DELETE_NOW("未通过审核，随注销一起删除"),
    WAIT_UNFREEZE("已冻结，暂时不能注销");

    private final String text;

    CancelFarmDisposal(String text) {
        this.text = text;
    }
}