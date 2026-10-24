package com.zhimu.server.admin.content.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 管理端文件状态（ADM-S34 / S36 / S37 / S38）。 */
@Data
public class AdminFileVO {

    private Long fileId;
    private String scene;
    private String originalName;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private String uploadStatus;
    private String transcodeStatus;
    private String status;
    private String transcodeError;
    private String url;
    private Long coverFileId;
    private String coverUrl;
    private String hlsUrl;
    private LocalDateTime createdAt;
    private List<VariantVO> variants = new ArrayList<>();
    private List<CoverCandidateVO> coverCandidates = new ArrayList<>();

    @Data
    public static class VariantVO {
        private String variantCode;
        private String status;
        private java.math.BigDecimal progress;
        private Integer width;
        private Integer height;
        private Long fileSize;
        private String errorMessage;
    }
}
