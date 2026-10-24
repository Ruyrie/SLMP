package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 只带乐观锁 version 的请求（ADM-C31 课程上架、ADM-C33 删除草稿、ADM-C53 知识上架）。 */
@Data
public class VersionRequest {

    @NotNull(message = "不能为空")
    private Integer version;
}
