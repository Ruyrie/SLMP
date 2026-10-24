package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D37 停用批号。 */
@Data
public class LotRecallRequest {

    @NotNull(message = "请选择药品")
    private Long catalogId;

    @NotBlank(message = "请填写生产批号")
    @Size(max = 30, message = "生产批号最多 30 个字")
    private String lotNo;

    @Size(max = 100, message = "停用原因最多 100 个字")
    private String reason;
}
