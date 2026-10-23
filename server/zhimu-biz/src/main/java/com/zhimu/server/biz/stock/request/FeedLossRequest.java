package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** API-M18 登记报损（App {@code FeedLossRequest}）：报损日期固定今天。 */
@Data
public class FeedLossRequest {

    @NotNull(message = "请选择饲料")
    private Long feedId;

    @NotNull(message = "必须大于 0")
    @DecimalMin(value = "0", inclusive = false, message = "必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "最多两位小数")
    private BigDecimal inputQty;

    private String inputUnit;

    @NotBlank(message = "请选择报损原因")
    private String reason;

    @Size(max = 100, message = "最多 100 个字")
    private String remark;

    @NotNull(message = "库存已变化，请刷新后重试")
    private Integer stockVersion;
}
