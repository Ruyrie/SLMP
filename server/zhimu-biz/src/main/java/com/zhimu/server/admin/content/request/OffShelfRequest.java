package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C32 下架课程 / ADM-C39 下架专题 / ADM-C52 下架知识。 */
@Data
public class OffShelfRequest {

    @NotBlank(message = "请填写下架原因")
    @Size(max = 100, message = "下架原因不能超过 100 字")
    private String offReason;

    @NotNull(message = "不能为空")
    private Integer version;
}
