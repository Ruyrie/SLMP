package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C39 专题上架 / 下架。 */
@Data
public class TopicStatusRequest {

    @NotBlank(message = "请选择状态")
    private String status;

    @Size(max = 100, message = "下架原因不能超过 100 字")
    private String reason;

    @NotNull(message = "不能为空")
    private Integer version;
}
