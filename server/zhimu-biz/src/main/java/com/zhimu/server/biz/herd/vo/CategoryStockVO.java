package com.zhimu.server.biz.herd.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-L05 按品类存栏（L03 品类视图）；batches 只含在养批次。 */
@Data
public class CategoryStockVO {

    private Long categoryId;
    private String categoryName;
    private Boolean isBreeding;
    private Integer stock;
    private Integer batchCount;
    private BigDecimal mortalityRate;
    private List<String> penNames;
    private List<BatchListItemVO> batches;
    private String unitText;
}
