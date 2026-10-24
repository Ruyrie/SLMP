package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D33 编辑禁用药别名与类别。 */
@Data
public class BannedDrugUpdateRequest {

    @Size(max = 100, message = "别名最多 100 个字")
    private String alias;

    /** BANNED_CLASS */
    @NotBlank(message = "请选择禁用类别")
    private String bannedClass;

    @NotNull(message = "缺少版本号")
    private Integer version;
}
