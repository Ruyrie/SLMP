package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D13 回退标准版本：复制目标版本内容生成新的线上版本。 */
@Data
public class StdRollbackRequest {

    @NotNull(message = "请选择要回退的版本")
    private Integer targetVersionNo;

    /** 为空由 Service 返回 40004“请填写回退原因” */
    @Size(max = 100, message = "回退原因最多 100 个字")
    private String reason;

    @NotNull(message = "缺少当前线上版本号")
    private Integer onlineVersionNo;
}
