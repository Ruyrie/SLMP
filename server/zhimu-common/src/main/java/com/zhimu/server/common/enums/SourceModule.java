package com.zhimu.server.common.enums;

import lombok.Getter;

/** 修改申请来源模块 */
@Getter
public enum SourceModule implements CodeEnum {
    PEN_STOCK("栏舍存栏"),
    BREEDING("繁育"),
    FEEDING("饲喂称重"),
    INSPECTION("巡检"),
    HEALTH("免疫用药消毒"),
    INVENTORY("物资库存"),
    SALE("出栏");

    private final String text;

    SourceModule(String text) {
        this.text = text;
    }
}