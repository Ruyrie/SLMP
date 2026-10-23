package com.zhimu.server.biz.stock.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-M15 确认盘点（App {@code StocktakeConfirmRequest}）：只含已录入实盘的饲料。 */
@Data
public class StocktakeConfirmRequest {

    @NotEmpty(message = "至少录入一种饲料的实盘数量")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "请选择饲料")
        private Long feedId;

        @NotNull(message = "账面结存已变化，请刷新后重新核对")
        private BigDecimal bookQty;

        @NotNull(message = "账面结存已变化，请刷新后重新核对")
        private Integer stockVersion;

        @NotNull(message = "实盘数量不能小于 0")
        @DecimalMin(value = "0", message = "实盘数量不能小于 0")
        @Digits(integer = 10, fraction = 2, message = "最多两位小数")
        private BigDecimal actualQty;
    }
}
