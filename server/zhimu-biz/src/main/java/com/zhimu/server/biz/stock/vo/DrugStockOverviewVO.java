package com.zhimu.server.biz.stock.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.QtyVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** API-M24 兽药疫苗概览（App {@code DrugStockOverviewVO}）。 */
@Data
public class DrugStockOverviewVO {

    private LotCounts lotCounts;
    private NearDays nearDays;
    private LowStockBanner lowStockBanner;
    private int attentionCount;
    private int normalCount;
    private boolean hasAnyLot;
    private boolean farmReadonly;

    @Data
    public static class LotCounts {
        private int all;
        private int vetDrug;
        private int vaccine;
        private int disinfectant;
    }

    @Data
    public static class NearDays {
        private int vet;
        private int vaccine;
        private int disinfectant;
    }

    @Data
    public static class LowStockBanner {
        private List<LowDrug> drugs = new ArrayList<>();
        private Long preselectDrugId;
    }

    @Data
    public static class LowDrug {
        private long drugId;
        private String name;
        private QtyVO usable;
        @ManagerView
        private BigDecimal minStock;
    }
}
