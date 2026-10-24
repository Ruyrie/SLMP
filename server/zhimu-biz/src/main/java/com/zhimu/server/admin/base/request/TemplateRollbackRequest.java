package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模板回退：把历史版本项目复制为新草稿，发布后生效。 */
@Data
public class TemplateRollbackRequest {

    @NotNull(message = "请选择要回退的版本")
    private Integer targetVersionNo;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
