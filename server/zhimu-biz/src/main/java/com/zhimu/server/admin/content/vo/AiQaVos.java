package com.zhimu.server.admin.content.vo;

import com.zhimu.server.common.vo.FileVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** WA-C05 AI 问答与更正的响应模型（ADM-C57–C65）。 */
public final class AiQaVos {

    private AiQaVos() {
    }

    @Data
    public static class SummaryVO {
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private Integer qaCount = 0;
        private Integer askerCount = 0;
        private BigDecimal usefulRate;
        private Integer ratedCount = 0;
        private Integer usefulCount = 0;
        private Integer pendingUselessCount = 0;
        private Integer noBasisCount = 0;
        private BigDecimal noBasisRatio;
    }

    @Data
    public static class RowVO {
        private Long messageId;
        private Long sessionId;
        private Integer roundNo;
        private String questionText;
        private Integer imageCount = 0;
        private Long farmId;
        private String farmName;
        private Long askerId;
        private String askerName;
        private String batchNo;
        private String answerType;
        private String answerTypeText;
        private String rating;
        private String ratingText;
        private Boolean canRate;
        private String handleStatus;
        private String handleStatusText;
        private Boolean isSpotCheck = false;
        private LocalDateTime askedAt;
    }

    @Data
    public static class SessionVO {
        private Long sessionId;
        private String farmName;
        private String askerName;
        private String askerRole;
        private LocalDateTime startedAt;
        private Integer roundCount;
    }

    @Data
    public static class CitationVO {
        private Integer seqNo;
        private String title;
        private String sourceType;
        private String iconType;
        private Long knowledgeId;
        private Integer versionNo;
        private String paraKey;
    }

    @Data
    public static class RoundVO {
        private Long messageId;
        private Integer roundNo;
        private LocalDateTime askedAt;
        private String questionText;
        private List<FileVO> images = new ArrayList<>();
        private String status;
        private String answerType;
        private String answerTypeText;
        private String answerText;
        private List<CitationVO> citations = new ArrayList<>();
        private String rating;
        private String ratingText;
        private LocalDateTime ratedAt;
    }

    @Data
    public static class SpotCheckVO {
        private String title;
        private String content;
        private LocalDateTime checkedAt;
    }

    @Data
    public static class CorrectionVO {
        private Long correctionId;
        private Long messageId;
        private String content;
        private String status;
        private String statusText;
        private LocalDateTime publishedAt;
        private LocalDateTime firstPublishedAt;
        private String publishedByName;
        private LocalDateTime withdrawnAt;
        private String withdrawReason;
        private Long taskId;
        private Integer version;
    }

    @Data
    public static class TaskRefVO {
        private Long id;
        private String title;
        private String status;
    }

    @Data
    public static class KnowledgeTargetVO {
        private Long knowledgeId;
        private String title;
    }

    @Data
    public static class DetailVO {
        private Long messageId;
        private String handleStatus;
        private String handleStatusText;
        private Integer focusRoundNo;
        private SessionVO session;
        private Map<String, Object> context;
        private List<RoundVO> rounds = new ArrayList<>();
        private SpotCheckVO spotCheck;
        private CorrectionVO correction;
        private KnowledgeTargetVO suggestTaskTarget;
        private TaskRefVO task;
        private Boolean canCorrect = false;
        private Boolean canUpdateCorrection = false;
        private Boolean canNoAction = false;
        private Boolean canCreateTask = false;
        private String answerType;
        private String answerTypeText;
        private String rating;
        private String ratingText;
    }

    @Data
    public static class TaskSuggestionVO {
        private String suggestedTitle;
        private Long suggestedCategoryId;
        private Integer similarCount30d = 0;
        private List<String> similarKeywords = new ArrayList<>();
    }

    @Data
    public static class CorrectionResultVO {
        private Long correctionId;
        private String status;
        private String statusText;
        private Long taskId;
        private Integer version;
    }

    @Data
    public static class ExportVO {
        private Long fileId;
        private String fileName;
        private String status;
        private String url;
        private Integer rowCount;
    }
}
