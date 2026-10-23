package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M08 饲料入库（App {@code FeedInboundRequest}）：input_unit 默认 MEASURE。 */
@Data
public class FeedInboundRequest {

    @NotNull(message = "请选择饲料")
    private Long feedId;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "最多两位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "最多两位小数")
    private BigDecimal unitPrice;

    private LocalDate productionDate;

    private LocalDate shelfLifeDate;

    @NotBlank(message = "请填写供应商")
    @Size(max = 30, message = "最多 30 个字")
    private String supplierName;

    @NotNull(message = "请选择入库日期")
    private LocalDate inboundDate;

    private Boolean syncFinance;
}
