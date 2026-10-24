package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 编辑条目抽屉（ADM-C45）。 */
@Data
public class KnowledgeEditVO {

    private Long id;
    private String sourceType;
    private String status;
    private Boolean isMajorEpidemic;
    private String displayType;
    private Integer version;
    private OnlineInfo online;
    private ContentSnapshot onlineContent;
    private Draft draft;
    private DraftOwner draftOwner;
    private Integer citeCount30d;
    private BigDecimal usefulRate30d;
    private LocalDateTime statAt;
    private List<ParagraphInfo> paragraphs = new ArrayList<>();

    @Data
    public static class OnlineInfo {
        private Integer versionNo;
        private LocalDateTime publishedAt;
        private String publishedByName;
    }

    @Data
    public static class ContentSnapshot {
        private String title;
        private Long categoryId;
        private String categoryName;
        private String displayType;
        private Boolean isMajorEpidemic;
        private List<Long> applicableCategoryIds = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();
        private String content;
    }

    @Data
    public static class Draft extends ContentSnapshot {
        private Long draftId;
        private String changeNote;
        private Long sourceTaskId;
        private String savedByName;
        private LocalDateTime savedAt;
    }

    @Data
    public static class DraftOwner {
        private String name;
        private LocalDateTime savedAt;
    }

    @Data
    public static class ParagraphInfo {
        private String paraKey;
        private Integer citeCount30d;
        private Boolean isFrequent;
    }
}
