package com.zhimu.server.admin.content.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** ADM-C43 分类排序：ids 为全部分类按新顺序。 */
@Data
public class CategoryOrderRequest {

    @NotEmpty(message = "分类列表已变化，请刷新后重试")
    private List<Long> ids;
}
