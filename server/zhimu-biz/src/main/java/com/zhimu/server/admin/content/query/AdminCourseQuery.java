package com.zhimu.server.admin.content.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADM-C23 课程列表筛选。选专题时按节次排序，否则按更新时间倒序。 */
@Getter
@Setter
public class AdminCourseQuery extends PageQuery {

    private Long topicId;
    private Long categoryId;
    /** ALL 或空 = 全部；ON_SHELF / OFF_SHELF / DRAFT */
    private String status;
    private String transcodeStatus;

    @Size(max = 30, message = "搜索内容不能超过 30 字")
    private String keyword;

    public AdminCourseQuery() {
        setSize(10);
    }

    @Override
    public int sizeOrDefault() {
        Integer value = getSize();
        if (value == null || value < 1) {
            return 10;
        }
        return Math.min(value, MAX_SIZE);
    }
}
