package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D02 / D03 品类新增与编辑；D03 必须带 version。 */
@Data
public class CategorySaveRequest {

    @NotBlank(message = "请填写品类名称")
    @Size(max = 10, message = "品类名称最多 10 个字")
    private String name;

    @NotBlank(message = "请选择畜种")
    private String species;

    private Boolean isBreeding;

    @Min(value = 0, message = "排序不能小于 0")
    private Integer sortNo;

    /** BASE_STATUS：ENABLED / DISABLED；D02 默认 ENABLED */
    private String status;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String disableReason;

    /** D03 必填 */
    private Integer version;
}
