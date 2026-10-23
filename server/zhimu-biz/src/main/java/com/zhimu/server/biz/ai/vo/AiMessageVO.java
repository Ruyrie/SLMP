package com.zhimu.server.biz.ai.vo;

import com.zhimu.server.common.vo.FileVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮问答（API-K03、K08、SSE done.message）。正文含 [^n] 引用标记。
 */
@Data
public class AiMessageVO {

    private Long id;
    private Long sessionId;
    private int roundNo;
    private String askedAt;
    private String questionText;
    private List<FileVO> images = new ArrayList<>();
    private Long batchId;
    private String batchNo;
    private String contextCourseTitle;
    /** ANSWERING / ANSWERED / INTERRUPTED / TIMEOUT / FAILED */
    private String status;
    /** NORMAL / NO_BASIS / OFF_TOPIC / BANNED_DRUG / EPIDEMIC */
    private String answerType;
    private String answerText;
    private List<CitationBriefVO> citations = new ArrayList<>();
    /** USEFUL / USELESS */
    private String rating;
    private String ratedAt;
    private boolean canRate;
    private boolean canCopy;
    private boolean inContext;
    private CorrectionVO correction;
    private Integer errorCode;
    private String errorMessage;
    private String answeredAt;
}
