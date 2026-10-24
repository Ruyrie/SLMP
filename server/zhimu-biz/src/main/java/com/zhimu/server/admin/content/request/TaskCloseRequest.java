package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C56 关闭知识补充任务。 */
@Data
public class TaskCloseRequest {

    @NotBlank(message = "请填写关闭原因")
    @Size(max = 100, message = "关闭原因不能超过 100 字")
    private String closeReason;

    @NotNull(message = "不能为空")
    private Integer version;
}
