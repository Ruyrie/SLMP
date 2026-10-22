package com.zhimu.slmp.breed.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

/**
 * 产仔记录详情（API-B19）：B04 修改模式初值。
 */
public class FarrowingDetailVO {

    private long farrowingId;
    private long animalId;
    private long cycleId;
    @Nullable
    private Long targetPenId;
    @Nullable
    private Long offspringBreedId;
    @Nullable
    private Long batchId;
    @Nullable
    private String earTag;
    @Nullable
    private String farrowDate;
    @Nullable
    private String targetPenName;
    @Nullable
    private String offspringBreedName;
    @Nullable
    private String batchNo;
    @Nullable
    private String createdAt;
    private int parityNo;
    private int totalBorn;
    private int liveBorn;
    private int stillborn;
    private int version;
    @Nullable
    private Integer batchCurrentCount;
    private boolean transferToBatch;
    private boolean canRequestChange;
    @Nullable
    private UserBriefVO registrant;
    @Nullable
    private ChangeRequestStateVO changeState;

    public long getFarrowingId() {
        return farrowingId;
    }

    public long getAnimalId() {
        return animalId;
    }

    public long getCycleId() {
        return cycleId;
    }

    @Nullable
    public Long getTargetPenId() {
        return targetPenId;
    }

    @Nullable
    public Long getOffspringBreedId() {
        return offspringBreedId;
    }

    @Nullable
    public Long getBatchId() {
        return batchId;
    }

    @Nullable
    public String getEarTag() {
        return earTag;
    }

    @Nullable
    public String getFarrowDate() {
        return farrowDate;
    }

    @Nullable
    public String getTargetPenName() {
        return targetPenName;
    }

    @Nullable
    public String getOffspringBreedName() {
        return offspringBreedName;
    }

    @Nullable
    public String getBatchNo() {
        return batchNo;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    public int getParityNo() {
        return parityNo;
    }

    public int getTotalBorn() {
        return totalBorn;
    }

    public int getLiveBorn() {
        return liveBorn;
    }

    public int getStillborn() {
        return stillborn;
    }

    public int getVersion() {
        return version;
    }

    @Nullable
    public Integer getBatchCurrentCount() {
        return batchCurrentCount;
    }

    public boolean isTransferToBatch() {
        return transferToBatch;
    }

    public boolean isCanRequestChange() {
        return canRequestChange;
    }

    @Nullable
    public UserBriefVO getRegistrant() {
        return registrant;
    }

    @Nullable
    public ChangeRequestStateVO getChangeState() {
        return changeState;
    }
}
