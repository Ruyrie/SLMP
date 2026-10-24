package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 课程指标卡与导航计数（ADM-C22）。 */
@Data
public class CourseSummaryVO {

    private Integer onShelfCount;
    private Integer topicCount;
    private Integer categoryCount;
    private Long plays30d;
    private Long viewers30d;
    private BigDecimal avgFinishRate;
    private Integer transcodingCount;
    private Integer failedCount;
    private Nav nav = new Nav();

    @Data
    public static class Nav {
        private Integer total;
        private List<TopicNav> topics = new ArrayList<>();
        private List<CategoryNav> categories = new ArrayList<>();
    }

    @Data
    public static class TopicNav {
        private Long id;
        private String name;
        private Integer onShelfCount;
    }

    @Data
    public static class CategoryNav {
        private Long id;
        private String name;
        private Integer count;
    }
}
