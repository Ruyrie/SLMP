package com.zhimu.server.biz.breeding.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 繁育记录（API-B06、B13、B16、B18、B21）。 */
@Getter
@Setter
public class BreedingRecordVO {

    /** MATING / PREG_CHECK / FARROWING / WEANING / BREEDING_EXIT */
    private String recordType;
    private String recordTypeText;
    /** BREEDING_EXIT 为种畜 ID */
    private long recordId;
    private String recordDate;
    private String matingMethod;
    private String matingMethodText;
    private String boarInfo;
    private String checkResult;
    private String checkResultText;
    private String checkMethod;
    private String checkMethodText;
    private Integer parityNo;
    private Integer totalBorn;
    private Integer liveBorn;
    private Integer stillborn;
    private Long batchId;
    private String batchNo;
    private Integer weanedCount;
    private BigDecimal litterWeight;
    private String exitType;
    private String exitTypeText;
    private String exitReason;
    private UserBriefVO registrant;
    private String createdAt;
    private ChangeRequestStateVO changeState;
    private boolean canRequestChange;
    private boolean hasChangeLog;
    private Integer version;
}
