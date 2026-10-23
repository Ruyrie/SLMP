package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** API-M41 保存临期天数（App {@code StockSettingRequest}）：首次保存 version 为 0。 */
@Data
public class StockSettingRequest {

    @NotNull(message = "请填写临期天数")
    @Min(value = 1, message = "临期天数必须是 1–365")
    @Max(value = 365, message = "临期天数必须是 1–365")
    private Integer nearDaysVet;

    @NotNull(message = "请填写临期天数")
    @Min(value = 1, message = "临期天数必须是 1–365")
    @Max(value = 365, message = "临期天数必须是 1–365")
    private Integer nearDaysVaccine;

    @NotNull(message = "请填写临期天数")
    @Min(value = 1, message = "临期天数必须是 1–365")
    @Max(value = 365, message = "临期天数必须是 1–365")
    private Integer nearDaysDisinfectant;

    @NotNull(message = "数据已被他人修改，请刷新后重试")
    private Integer version;
}
