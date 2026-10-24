package com.zhimu.server.admin.base.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-D20 / D45 模板项目拖动排序。 */
@Data
public class TemplateItemOrderRequest {

    @NotEmpty(message = "请选择要排序的项目")
    private List<Long> itemIds = new ArrayList<>();

    @NotNull(message = "缺少版本号")
    private Integer version;
}
