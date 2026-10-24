package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-C41 新增分类 / ADM-C42 改名与停用启用。 */
@Data
public class ContentCategorySaveRequest {

    @NotBlank(message = "请填写分类名称")
    @Size(max = 10, message = "分类名称不能超过 10 字")
    private String name;

    private Integer sortNo;
    /** ENABLED / DISABLED；新增不传默认 ENABLED */
    private String status;
    private Integer version;
}
