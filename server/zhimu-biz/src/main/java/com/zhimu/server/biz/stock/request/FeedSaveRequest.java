package com.zhimu.server.biz.stock.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** API-M05 / M06 饲料档案（App {@code FeedSaveRequest}）。修改时 edit_version 必填。 */
@Data
public class FeedSaveRequest {

    @NotBlank(message = "请填写名称")
    @Size(max = 20, message = "最多 20 个字")
    private String name;

    @NotBlank(message = "请重新选择类型")
    private String feedTypeCode;

    @Size(max = 20, message = "最多 20 个字")
    private String spec;

    @Size(max = 4, message = "最多 4 个字")
    private String packUnit;

    @DecimalMin(value = "0", inclusive = false, message = "请填写每袋千克数")
    private BigDecimal packSize;

    private Long categoryId;

    @DecimalMin(value = "0", message = "不能小于 0")
    private BigDecimal minStock;

    @Min(value = 1, message = "必须是大于 0 的整数")
    @Max(value = 365, message = "必须是大于 0 的整数")
    private Integer warnDays;

    private Integer editVersion;
}
