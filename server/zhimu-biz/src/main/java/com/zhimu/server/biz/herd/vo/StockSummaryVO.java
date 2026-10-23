package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-L03 存栏汇总（L03 汇总卡）。 */
@Data
public class StockSummaryVO {

    private Integer totalStock;
    private Integer activeBatchCount;
    /** 待模块08接入：休药中批次（当前无用药数据，先为 0） */
    private Integer withdrawalBatchCount;
    private Integer endedBatchCount;
    private Integer entryTotal;
    private Integer deathTotal;
    private Integer capacityTotal;
    private Integer commercialPenCount;
    private BigDecimal mortalityRate;
    private BigDecimal occupancyRate;
    private String unit;
    private String unitText;
    private List<CategoryShareVO> categories;

    /** 品类占比 */
    @Data
    public static class CategoryShareVO {
        private Long categoryId;
        private String categoryName;
        private Integer stock;
        private Integer batchCount;
        private List<StageGroupItem> stageGroups;
    }

    /** 品类内按生长阶段的存栏；阶段表待管理端-03 的饲喂标准发布后接入，当前按品类汇总 */
    @Data
    public static class StageGroupItem {
        private String stageShortName;
        private Integer stock;
    }
}
