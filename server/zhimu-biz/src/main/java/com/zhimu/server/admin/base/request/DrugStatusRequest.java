package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D28 停用 / 启用药品。 */
@Data
public class DrugStatusRequest {

    /** BASE_STATUS：ENABLED / DISABLED */
    @NotBlank(message = "请选择状态")
    private String status;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String reason;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
