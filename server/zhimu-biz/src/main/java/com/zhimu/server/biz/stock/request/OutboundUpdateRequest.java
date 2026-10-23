package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** API-M22 当天修改出库单（App {@code OutboundUpdateRequest}）：出库类型、物资、批号不可改。 */
@Data
public class OutboundUpdateRequest {

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "最多三位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    private String destination;

    private String targetType;

    private Long batchId;

    private Long penId;

    private Long receiverId;

    @Size(max = 30, message = "最多 30 个字")
    private String returnTo;

    private String returnReason;

    @Size(max = 100, message = "最多 100 个字")
    private String remark;

    private LocalDate outDate;

    @NotNull(message = "数据已被他人修改，请刷新后重试")
    private Integer version;
}
