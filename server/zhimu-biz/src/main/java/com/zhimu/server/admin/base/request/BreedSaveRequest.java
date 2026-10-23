package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D05 / D06 品种新增与编辑；D06 必须带 version。 */
@Data
public class BreedSaveRequest {

    @NotNull(message = "请选择所属品类")
    private Long categoryId;

    @NotBlank(message = "请填写品种名称")
    @Size(max = 20, message = "品种名称最多 20 个字")
    private String name;

    @Size(max = 100, message = "品种说明最多 100 个字")
    private String description;

    @Min(value = 1, message = "妊娠期必须在 1–400 天之间")
    @Max(value = 400, message = "妊娠期必须在 1–400 天之间")
    private Integer gestationDays;

    @Min(value = 1, message = "孵化期必须在 1–400 天之间")
    @Max(value = 400, message = "孵化期必须在 1–400 天之间")
    private Integer incubationDays;

    @Min(value = 1, message = "待妊检天数必须大于 0")
    private Integer pregnancyCheckDays;

    private Long offspringBreedId;

    @Min(value = 0, message = "排序不能小于 0")
    private Integer sortNo;

    private String status;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String disableReason;

    /** 停用或修改妊娠期、孵化期、待妊检天数时必填 */
    @Size(max = 100, message = "修改原因最多 100 个字")
    private String changeReason;

    /** D06 必填 */
    private Integer version;
}
