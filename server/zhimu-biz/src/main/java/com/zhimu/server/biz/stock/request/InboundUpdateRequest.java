package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M10 修改未出库入库单（App {@code InboundUpdateRequest}）：饲料带生产日期、保质期、供应商，药品带有效期。 */
@Data
public class InboundUpdateRequest {

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "最多三位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "最多两位小数")
    private BigDecimal unitPrice;

    @NotNull(message = "请选择入库日期")
    private LocalDate inboundDate;

    private Boolean syncFinance;

    private LocalDate productionDate;

    private LocalDate shelfLifeDate;

    @Size(max = 30, message = "最多 30 个字")
    private String supplierName;

    private LocalDate expireDate;

    @NotNull(message = "数据已被他人修改，请刷新后重试")
    private Integer version;
}
