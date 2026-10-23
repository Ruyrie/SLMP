package com.zhimu.server.biz.breeding.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Getter;
import lombok.Setter;

/** 产仔记录详情（API-B19）。 */
@Getter
@Setter
public class FarrowingDetailVO {

    private long farrowingId;
    private long animalId;
    private long cycleId;
    private Long targetPenId;
    private Long offspringBreedId;
    private Long batchId;
    private String earTag;
    private String farrowDate;
    private String targetPenName;
    private String offspringBreedName;
    private String batchNo;
    private String createdAt;
    private int parityNo;
    private int totalBorn;
    private int liveBorn;
    private int stillborn;
    private int version;
    private Integer batchCurrentCount;
    private boolean transferToBatch;
    private boolean canRequestChange;
    private UserBriefVO registrant;
    private ChangeRequestStateVO changeState;
}
