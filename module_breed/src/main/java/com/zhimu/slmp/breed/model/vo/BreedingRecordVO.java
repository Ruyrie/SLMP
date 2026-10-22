package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;

/**
 * 繁育记录（API-B06，B06 繁育记录；B13、B16、B18、B21 的响应）。
 * version 为待定事项 1 的客户端补充字段（服务端尚未列出），修改记录时提交。
 */
public class BreedingRecordVO {

    /** MATING / PREG_CHECK / FARROWING / WEANING / BREEDING_EXIT */
    @Nullable
    private String recordType;
    @Nullable
    private String recordTypeText;
    /** BREEDING_EXIT 为种畜 ID */
    private long recordId;
    @Nullable
    private String recordDate;
    @Nullable
    private String matingMethod;
    @Nullable
    private String matingMethodText;
    @Nullable
    private String boarInfo;
    @Nullable
    private String checkResult;
    @Nullable
    private String checkResultText;
    @Nullable
    private String checkMethod;
    @Nullable
    private String checkMethodText;
    @Nullable
    private Integer parityNo;
    @Nullable
    private Integer totalBorn;
    @Nullable
    private Integer liveBorn;
    @Nullable
    private Integer stillborn;
    @Nullable
    private Long batchId;
    @Nullable
    private String batchNo;
    @Nullable
    private Integer weanedCount;
    @Nullable
    private BigDecimal litterWeight;
    @Nullable
    private String exitType;
    @Nullable
    private String exitTypeText;
    @Nullable
    private String exitReason;
    @Nullable
    private UserBriefVO registrant;
    @Nullable
    private String createdAt;
    @Nullable
    private ChangeRequestStateVO changeState;
    private boolean canRequestChange;
    private boolean hasChangeLog;
    /** 待定事项 1：记录 version */
    @Nullable
    private Integer version;

    @Nullable
    public String getRecordType() {
        return recordType;
    }

    @Nullable
    public String getRecordTypeText() {
        return recordTypeText;
    }

    public long getRecordId() {
        return recordId;
    }

    @Nullable
    public String getRecordDate() {
        return recordDate;
    }

    @Nullable
    public String getMatingMethod() {
        return matingMethod;
    }

    @Nullable
    public String getMatingMethodText() {
        return matingMethodText;
    }

    @Nullable
    public String getBoarInfo() {
        return boarInfo;
    }

    @Nullable
    public String getCheckResult() {
        return checkResult;
    }

    @Nullable
    public String getCheckResultText() {
        return checkResultText;
    }

    @Nullable
    public String getCheckMethod() {
        return checkMethod;
    }

    @Nullable
    public String getCheckMethodText() {
        return checkMethodText;
    }

    @Nullable
    public Integer getParityNo() {
        return parityNo;
    }

    @Nullable
    public Integer getTotalBorn() {
        return totalBorn;
    }

    @Nullable
    public Integer getLiveBorn() {
        return liveBorn;
    }

    @Nullable
    public Integer getStillborn() {
        return stillborn;
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
    public Integer getWeanedCount() {
        return weanedCount;
    }

    @Nullable
    public BigDecimal getLitterWeight() {
        return litterWeight;
    }

    @Nullable
    public String getExitType() {
        return exitType;
    }

    @Nullable
    public String getExitTypeText() {
        return exitTypeText;
    }

    @Nullable
    public String getExitReason() {
        return exitReason;
    }

    @Nullable
    public UserBriefVO getRegistrant() {
        return registrant;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }

    public boolean isCanRequestChange() {
        return canRequestChange;
    }

    public boolean isHasChangeLog() {
        return hasChangeLog;
    }

    @Nullable
    public Integer getVersion() {
        return version;
    }
}
