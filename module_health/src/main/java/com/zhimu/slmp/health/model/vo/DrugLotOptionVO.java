package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.math.BigDecimal;
import com.zhimu.slmp.common.model.vo.QtyVO;

/**
 * API-M27 批号选项的一项：lot_status 为 NORMAL / NEAR_EXPIRY / EXPIRED / RECALLED / BANNED；avg_price 员工不输出。
 */
public class DrugLotOptionVO {

    private long lotId;
    @Nullable
    private String lotNo;
    @Nullable
    private String expireDate;
    @Nullable
    private String lotStatus;
    @Nullable
    private Integer daysToExpire;
    /** 结存（QtyVO，按模块09 API-M27） */
    @Nullable
    private QtyVO stock;
    @Nullable
    private BigDecimal avgPrice;
    /** 先到期先出（默认项） */
    private boolean priorityOut;
    /** 结存小于 need_qty */
    private boolean insufficient;
    private boolean canGoScrap;

    public long getLotId() {
        return lotId;
    }

    @Nullable
    public String getLotNo() {
        return lotNo;
    }

    @Nullable
    public String getExpireDate() {
        return expireDate;
    }

    @Nullable
    public String getLotStatus() {
        return lotStatus;
    }

    @Nullable
    public Integer getDaysToExpire() {
        return daysToExpire;
    }

    /** 结存数量（记账单位） */
    @Nullable
    public BigDecimal getStock() {
        return stock == null ? null : stock.value;
    }

    @Nullable
    public QtyVO getStockQty() {
        return stock;
    }

    @Nullable
    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public boolean isPriorityOut() {
        return priorityOut;
    }

    public boolean isInsufficient() {
        return insufficient;
    }

    public boolean isCanGoScrap() {
        return canGoScrap;
    }
}
