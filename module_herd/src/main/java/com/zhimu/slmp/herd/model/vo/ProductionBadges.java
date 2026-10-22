package com.zhimu.slmp.herd.model.vo;

/**
 * L01 功能入口小圆点：用药与休药（有休药中的批次）、饲料库存（有库存不足）。
 */
public class ProductionBadges {

    private boolean withdrawal;
    private boolean feedLowStock;

    public boolean isWithdrawal() {
        return withdrawal;
    }

    public boolean isFeedLowStock() {
        return feedLowStock;
    }
}
