package com.zhimu.server.admin.content.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-C44 知识条目列表筛选。 */
@Getter
@Setter
public class AdminKnowledgeQuery extends PageQuery {

    private String status;
    private Long categoryId;
    private String sourceType;

    @Size(max = 30, message = "搜索内容不能超过 30 字")
    private String keyword;
}
