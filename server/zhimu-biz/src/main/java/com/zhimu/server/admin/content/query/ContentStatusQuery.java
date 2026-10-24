package com.zhimu.server.admin.content.query;

import lombok.Getter;
import lombok.Setter;

/** ADM-C36 专题列表 / ADM-C40 分类列表的状态筛选（可空 = 全部）。 */
@Getter
@Setter
public class ContentStatusQuery {

    private String status;
}
