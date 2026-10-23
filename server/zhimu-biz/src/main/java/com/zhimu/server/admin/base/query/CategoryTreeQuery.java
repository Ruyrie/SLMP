package com.zhimu.server.admin.base.query;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** ADM-D01 品类品种树查询。 */
@Data
public class CategoryTreeQuery {

    /** 匹配品种名称或说明，≤ 20 字 */
    @Size(max = 20, message = "搜索内容不能超过 20 个字")
    private String keyword;
}
