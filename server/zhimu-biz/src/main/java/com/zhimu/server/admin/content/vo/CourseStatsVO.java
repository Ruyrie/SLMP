package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 课程数据抽屉（ADM-C34）。 */
@Data
public class CourseStatsVO {

    private Integer playCount;
    private Integer viewerCount;
    private Integer finishCount;
    private BigDecimal finishRate;
    private Integer avgWatchSec;
    private List<Daily> daily = new ArrayList<>();
    private List<Bucket> progressBuckets = new ArrayList<>();
    private ExitRange topExitRange;
    private Share clientShare = new Share();
    private Share qualityShare = new Share();
    private Integer mobilePlayCount;
    private Integer askAiCount;
    private Integer knowledgeClickCount;
    private TopicStat topic;
    private LocalDateTime statAt;

    @Data
    public static class Daily {
        private String date;
        private Integer plays;
    }

    @Data
    public static class Bucket {
        private String bucket;
        private Integer users;
    }

    @Data
    public static class ExitRange {
        private Integer startSec;
        private Integer endSec;
    }

    @Data
    public static class Share {
        private BigDecimal app;
        private BigDecimal web;
        private BigDecimal p720;
        private BigDecimal p480;
    }

    @Data
    public static class TopicStat {
        private Integer learners;
        private Integer allFinished;
        private BigDecimal avgFinished;
        private Integer totalSections;
    }
}
