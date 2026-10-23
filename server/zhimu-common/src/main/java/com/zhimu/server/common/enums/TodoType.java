package com.zhimu.server.common.enums;

import lombok.Getter;

/** 待办类型（全系统统一 13 个编码） */
@Getter
public enum TodoType implements CodeEnum {
    FEEDING("饲喂"),
    WEIGHING("称重"),
    INSPECTION("巡检"),
    IMMUNIZATION("免疫"),
    DISINFECTION("消毒"),
    FARROWING("预产"),
    HATCHING("预计出雏"),
    FEED_LOW_STOCK("饲料库存偏低"),
    DRUG_LOW_STOCK("药品库存偏低"),
    DRUG_NEAR_EXPIRY("药品临期"),
    DRUG_EXPIRED("药品过期"),
    WITHDRAWAL_END("休药期到期"),
    RECEIVABLE("待收款");

    private final String text;

    TodoType(String text) {
        this.text = text;
    }
}