package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M31 登记报废（App {@code DrugScrapRequest}）：默认全部结存，PLATFORM_RECALL 仅停用批号可选。 */
@Data
public class DrugScrapRequest {

    @NotNull(message = "请选择库存批号")
    private Long drugLotId;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "最多三位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    @NotBlank(message = "请选择报废原因")
    private String reason;

    @Size(max = 100, message = "最多 100 个字")
    private String remark;

    private LocalDate scrapDate;

    @NotNull(message = "库存已变化，请刷新后重试")
    private Integer stockVersion;
}
