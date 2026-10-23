package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M32 领用 / 退货（App {@code DrugOutboundRequest}）。 */
@Data
public class DrugOutboundRequest {

    @NotBlank(message = "请选择出库类型")
    private String outType;

    @NotNull(message = "请选择药品")
    private Long drugId;

    @NotNull(message = "请选择库存批号")
    private Long drugLotId;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "最多三位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    private String targetType;

    private Long batchId;

    private Long penId;

    private Long receiverId;

    @Size(max = 30, message = "最多 30 个字")
    private String returnTo;

    private String returnReason;

    @Size(max = 100, message = "最多 100 个字")
    private String remark;

    @NotNull(message = "请选择出库日期")
    private LocalDate outDate;

    @NotNull(message = "库存已变化，请刷新后重试")
    private Integer stockVersion;
}
