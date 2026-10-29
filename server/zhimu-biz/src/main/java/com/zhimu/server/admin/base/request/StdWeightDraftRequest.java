package com.zhimu.server.admin.base.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** ADM-D08 std_type = weight 保存草稿。 */
@Data
public class StdWeightDraftRequest {

    @NotNull(message = "请填写允许偏差")
    @DecimalMin(value = "0.01", message = "允许偏差必须在 0.01–0.50 之间")
    @DecimalMax(value = "0.50", message = "允许偏差必须在 0.01–0.50 之间")
    private BigDecimal toleranceRate;

    @NotNull(message = "请填写建议每月称重次数")
    @Min(value = 1, message = "建议每月称重次数必须在 1–8 之间")
    @Max(value = 8, message = "建议每月称重次数必须在 1–8 之间")
    private Integer suggestedWeighTimes;

    @NotBlank(message = "请选择生效方式")
    private String effectiveMode;

    private LocalDate effectiveDate;

    @NotEmpty(message = "至少填写 2 个日龄点")
    @Valid
    private List<Point> points = new ArrayList<>();

    /** 已有草稿时必填 */
    private Integer version;

    /** 暂存在草稿上的更新说明（选填，发布时以发布请求为准） */
    @Size(max = 100, message = "更新说明最多 100 个字")
    private String updateNote;

    @Data
    public static class Point {
        @NotNull(message = "请填写日龄")
        @Min(value = 1, message = "日龄必须在 1–999 之间")
        @Max(value = 999, message = "日龄必须在 1–999 之间")
        private Integer dayAge;

        @NotNull(message = "请填写标准体重")
        @DecimalMin(value = "0.01", message = "标准体重必须大于 0")
        @Digits(integer = 8, fraction = 2, message = "标准体重最多保留 2 位小数")
        private BigDecimal stdWeight;
    }
}
