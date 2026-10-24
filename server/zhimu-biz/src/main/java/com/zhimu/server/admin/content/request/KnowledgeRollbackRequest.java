package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C51 回退版本。 */
@Data
public class KnowledgeRollbackRequest {

    @NotNull(message = "请选择要回退的版本")
    private Integer targetVersionNo;

    @NotBlank(message = "请填写回退原因")
    @Size(max = 100, message = "回退原因不能超过 100 字")
    private String reason;

    @NotNull(message = "不能为空")
    private Integer version;
}
