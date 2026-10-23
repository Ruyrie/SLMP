package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** API-M37 / M38 药品档案（App {@code DrugSaveRequest}）：修改时 category、catalog_id 不传。 */
@Data
public class DrugSaveRequest {

    private String category;

    private Long catalogId;

    @Size(max = 20, message = "最多 20 个字")
    private String spec;

    private String unit;

    @Size(max = 4, message = "最多 4 个字")
    private String packUnit;

    @DecimalMin(value = "0", inclusive = false, message = "每包装折合数必须大于 0")
    private BigDecimal packSize;

    @DecimalMin(value = "0", message = "不能小于 0")
    private BigDecimal minStock;

    private String minStockUnit;

    private Integer version;
}
