package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D54 字典项停用 / 启用。 */
@Data
public class DictStatusRequest {

    @NotBlank(message = "请选择状态")
    private String status;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String reason;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
