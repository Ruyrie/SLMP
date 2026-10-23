package com.zhimu.server.common.enums;

import lombok.Getter;

/** 修改申请影响范围 */
@Getter
public enum ChangeImpact implements CodeEnum {
    STOCK("存栏"),
    COST("成本"),
    INVENTORY("库存"),
    SELLABLE_DATE("可出栏日期"),
    INCOME("收入"),
    STATS("统计");

    private final String text;

    ChangeImpact(String text) {
        this.text = text;
    }
}