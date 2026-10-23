package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import java.util.List;
import com.zhimu.slmp.common.model.vo.QtyVO;

/** API-M24 兽药疫苗概览（M05）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugStockOverviewVO {
    public LotCounts lotCounts;
    public NearDays nearDays;
    public LowStockBanner lowStockBanner;
    public int attentionCount;
    public int normalCount;
    public boolean hasAnyLot;
    public boolean farmReadonly;

    public static class LotCounts {
        public int all;
        public int vetDrug;
        public int vaccine;
        public int disinfectant;
    }

    public static class NearDays {
        public int vet;
        public int vaccine;
        public int disinfectant;
    }

    public static class LowStockBanner {
        public List<LowDrug> drugs;
        public Long preselectDrugId;
    }

    public static class LowDrug {
        public long drugId;
        public String name;
        public QtyVO usable;
        public BigDecimal minStock;
    }
}
