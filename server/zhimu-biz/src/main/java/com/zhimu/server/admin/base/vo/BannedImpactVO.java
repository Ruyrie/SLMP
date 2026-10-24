package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** ADM-D31 列入禁用药前影响：已建档养殖场数与现有库存合计（按单位）。 */
@Getter
@Setter
public class BannedImpactVO {

    private Integer archiveFarmCount;
    private List<StockTotalVO> stockTotals = new ArrayList<>();

    @Getter
    @Setter
    public static class StockTotalVO {
        private BigDecimal qty;
        private String unit;
        private String unitText;
    }
}
