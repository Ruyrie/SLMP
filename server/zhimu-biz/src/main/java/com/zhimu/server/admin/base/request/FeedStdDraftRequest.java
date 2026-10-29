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
import java.util.ArrayList;
import java.util.List;

/** ADM-D08 std_type = feed 保存草稿。 */
@Data
public class FeedStdDraftRequest {

    @NotNull(message = "请填写投喂异常判定默认值")
    @DecimalMin(value = "0.01", message = "投喂异常判定默认值必须在 0.01–0.50 之间")
    @DecimalMax(value = "0.50", message = "投喂异常判定默认值必须在 0.01–0.50 之间")
    private BigDecimal deviationRate;

    @NotEmpty(message = "请至少填写 1 个饲喂阶段")
    @Valid
    private List<Stage> stages = new ArrayList<>();

    /** 已有草稿时必填 */
    private Integer version;

    /** 暂存在草稿上的更新说明（选填，发布时以发布请求为准） */
    @Size(max = 100, message = "更新说明最多 100 个字")
    private String updateNote;

    @Data
    public static class Stage {
        @NotBlank(message = "请填写阶段名称")
        @Size(max = 10, message = "阶段名称最多 10 个字")
        private String stageName;

        @NotBlank(message = "请填写阶段简称")
        @Size(max = 10, message = "阶段简称最多 10 个字")
        private String stageShortName;

        @NotNull(message = "请填写起始日龄")
        @Min(value = 1, message = "首段起始日龄必须大于 0")
        private Integer dayAgeStart;

        private Integer dayAgeEnd;

        @NotBlank(message = "请选择饲料类型")
        private String feedTypeCode;

        @NotBlank(message = "请填写建议饲料名称")
        @Size(max = 20, message = "建议饲料名称最多 20 个字")
        private String suggestedFeedName;

        @NotNull(message = "请填写每日投喂量")
        @DecimalMin(value = "0.001", message = "每日投喂量必须大于 0")
        @Digits(integer = 7, fraction = 3, message = "每日投喂量最多保留 3 位小数")
        private BigDecimal dailyFeed;

        @NotNull(message = "请填写每日餐次")
        @Min(value = 1, message = "每日餐次必须在 1–4 之间")
        @Max(value = 4, message = "每日餐次必须在 1–4 之间")
        private Integer mealsPerDay;

        @NotEmpty(message = "请填写投喂时间")
        private List<String> mealTimes = new ArrayList<>();
    }
}
