package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D32 列入禁用药：从目录标记时传 catalog_id，直接新增时传 name。 */
@Data
public class BannedDrugCreateRequest {

    private Long catalogId;

    @Size(max = 40, message = "名称最多 40 个字")
    private String name;

    @Size(max = 100, message = "别名最多 100 个字")
    private String alias;

    /** BANNED_CLASS */
    @NotBlank(message = "请选择禁用类别")
    private String bannedClass;

    @Size(max = 100, message = "列入原因最多 100 个字")
    private String reason;
}
