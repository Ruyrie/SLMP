package com.zhimu.server.biz.ai.query;

import com.zhimu.server.common.api.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * API-K18 课程列表：category_id、learn_status、keyword、sort = LATEST / DURATION_ASC。
 */
@Getter
@Setter
public class CourseQuery extends PageQuery {

    private Long categoryId;
    private String learnStatus;
    private String keyword;
    private String sort;
}
