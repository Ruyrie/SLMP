package com.zhimu.server.biz.ai.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API-K12 知识原文。
 */
@Data
public class KnowledgeDetailVO {

    private Long id;
    private String title;
    private String categoryName;
    private String sourceType;
    private String sourceTypeText;
    private int versionNo;
    private String updatedAt;
    private String shareUrl;
    private EmbeddedVideoVO embeddedVideo;
    private List<KnowledgeBlockVO> blocks = new ArrayList<>();
    private List<RelatedKnowledgeVO> relatedItems = new ArrayList<>();
    private String highlightParaKey;
    private Integer citedVersionNo;
    private boolean updatedAfterAnswer;
}
