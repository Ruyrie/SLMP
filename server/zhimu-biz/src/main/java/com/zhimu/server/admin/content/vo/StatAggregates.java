package com.zhimu.server.admin.content.vo;

import lombok.Data;

/**
 * 统计聚合查询的投影（供 admin.content.mapper.ContentStatMapper 使用）。
 */
public final class StatAggregates {

    private StatAggregates() {
    }

    @Data
    public static class CoursePlayAgg {
        private Long courseId;
        private Long plays;
        private Long viewers;
    }

    @Data
    public static class CourseFinishAgg {
        private Long courseId;
        private Long finishes;
    }

    @Data
    public static class RatingAgg {
        private Long useful;
        private Long rated;
    }

    @Data
    public static class ParagraphCiteAgg {
        private String paraKey;
        private Long citations;
    }

    @Data
    public static class CategoryRef {
        private Long id;
        private String name;
        private String status;
    }
}
