package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模板类写操作版本号（放弃修改、删除项目、排序等）。 */
@Data
public class VersionRequest {

    @NotNull(message = "缺少版本号")
    private Integer version;
}
