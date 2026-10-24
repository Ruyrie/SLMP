package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D16 新建 / 复制免疫模板。 */
@Data
public class ImmuneTemplateCreateRequest {

    @NotNull(message = "请选择品种")
    private Long breedId;

    @Size(max = 30, message = "模板名称最多 30 个字")
    private String name;

    /** 复制来源模板（已发布）；复制其线上项目为草稿 */
    private Long copyFromTemplateId;
}
