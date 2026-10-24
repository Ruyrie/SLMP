package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C48 发布知识新版本。 */
@Data
public class KnowledgePublishRequest {

    @NotBlank(message = "请填写修改说明")
    @Size(max = 100, message = "修改说明不能超过 100 字")
    private String changeNote;

    @NotNull(message = "不能为空")
    private Integer version;
}
