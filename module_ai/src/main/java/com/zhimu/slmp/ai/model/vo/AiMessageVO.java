package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zhimu.slmp.common.model.vo.FileVO;
import java.util.Collections;
import java.util.List;

/**
 * 一轮问答（API-K03、K08、SSE done.message）。status：ANSWERING / ANSWERED / INTERRUPTED / TIMEOUT / FAILED；answer_type：NORMAL / NO_BASIS / OFF_TOPIC / BANNED_DRUG / EPIDEMIC；正文含 [^n] 引用标记
 */
public class AiMessageVO {

    private long id;
    private long sessionId;
    private int roundNo;
    private String askedAt;
    private String questionText;
    private List<FileVO> images;
    private Long batchId;
    private String batchNo;
    private String contextCourseTitle;
    private String status;
    private String answerType;
    private String answerText;
    private String analysisSummary;
    private String sourceNote;
    private List<CitationBriefVO> citations;
    private String rating;
    private String ratedAt;
    private boolean canRate;
    private boolean canCopy;
    private boolean inContext;
    private CorrectionVO correction;
    private Integer errorCode;
    private String errorMessage;
    private String answeredAt;

    public long getId() {
        return id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    @Nullable
    public String getAskedAt() {
        return askedAt;
    }

    @Nullable
    public String getQuestionText() {
        return questionText;
    }

    @NonNull
    public List<FileVO> getImages() {
        return images == null ? Collections.emptyList() : images;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getContextCourseTitle() {
        return contextCourseTitle;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getAnswerType() {
        return answerType;
    }

    @Nullable
    public String getAnswerText() {
        return answerText;
    }

    @Nullable
    public String getAnalysisSummary() { return analysisSummary; }

    @Nullable
    public String getSourceNote() { return sourceNote; }

    public void setAnalysisSummary(@Nullable String summary) { analysisSummary = summary; }

    public void setSourceNote(@Nullable String note) { sourceNote = note; }

    @NonNull
    public List<CitationBriefVO> getCitations() {
        return citations == null ? Collections.emptyList() : citations;
    }

    @Nullable
    public String getRating() {
        return rating;
    }

    @Nullable
    public String getRatedAt() {
        return ratedAt;
    }

    public boolean isCanRate() {
        return canRate;
    }

    public boolean isCanCopy() {
        return canCopy;
    }

    public boolean isInContext() {
        return inContext;
    }

    @Nullable
    public CorrectionVO getCorrection() {
        return correction;
    }

    @Nullable
    public Integer getErrorCode() {
        return errorCode;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    @Nullable
    public String getAnsweredAt() {
        return answeredAt;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public void setRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public void setAskedAt(@Nullable String askedAt) {
        this.askedAt = askedAt;
    }

    public void setQuestionText(@Nullable String questionText) {
        this.questionText = questionText;
    }

    public void setImages(@Nullable List<FileVO> images) {
        this.images = images;
    }

    public void setBatchId(@Nullable Long batchId) {
        this.batchId = batchId;
    }

    public void setBatchNo(@Nullable String batchNo) {
        this.batchNo = batchNo;
    }

    public void setContextCourseTitle(@Nullable String contextCourseTitle) {
        this.contextCourseTitle = contextCourseTitle;
    }

    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    public void setAnswerType(@Nullable String answerType) {
        this.answerType = answerType;
    }

    public void setAnswerText(@Nullable String answerText) {
        this.answerText = answerText;
    }

    public void setCitations(@Nullable List<CitationBriefVO> citations) {
        this.citations = citations;
    }

    public void setRating(@Nullable String rating) {
        this.rating = rating;
    }

    public void setRatedAt(@Nullable String ratedAt) {
        this.ratedAt = ratedAt;
    }

    public void setCanRate(boolean canRate) {
        this.canRate = canRate;
    }

    public void setCanCopy(boolean canCopy) {
        this.canCopy = canCopy;
    }

    public void setInContext(boolean inContext) {
        this.inContext = inContext;
    }

    public void setCorrection(@Nullable CorrectionVO correction) {
        this.correction = correction;
    }

    public void setErrorCode(@Nullable Integer errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setAnsweredAt(@Nullable String answeredAt) {
        this.answeredAt = answeredAt;
    }
}
