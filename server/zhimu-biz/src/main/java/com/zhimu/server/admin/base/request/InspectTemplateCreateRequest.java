package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D41 新建巡检模板。 */
@Data
public class InspectTemplateCreateRequest {

    @NotNull(message = "请选择品类")
    private Long categoryId;

    @Size(max = 20, message = "模板名称最多 20 个字")
    private String name;
}
