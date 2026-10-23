package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M30 兽药疫苗入库（App {@code DrugInboundRequest}）：input_unit 默认 PACK。 */
@Data
public class DrugInboundRequest {

    @NotNull(message = "请选择药品")
    private Long drugId;

    @NotBlank(message = "请填写生产批号")
    @Size(max = 30, message = "批号最多 30 位")
    private String lotNo;

    @NotNull(message = "请选择入库日期")
    private LocalDate inboundDate;

    @NotNull(message = "请选择有效期")
    private LocalDate expireDate;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "最多三位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "最多两位小数")
    private BigDecimal unitPrice;

    private Boolean syncFinance;
}
