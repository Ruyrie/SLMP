package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D21 / D46 发布模板修改。 */
@Data
public class TemplatePublishRequest {

    @Size(max = 100, message = "修改说明最多 100 个字")
    private String changeNote;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
