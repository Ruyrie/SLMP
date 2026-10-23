package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D11 发布标准新版本。 */
@Data
public class StdPublishRequest {

    /** 为空由 Service 返回 40004“请填写更新说明” */
    @Size(max = 100, message = "更新说明最多 100 个字")
    private String updateNote;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
