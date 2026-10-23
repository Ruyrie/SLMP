package com.zhimu.server.biz.inspect.vo;

import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.util.List;

/** API-I10 巡检详情。edit_mode：NONE / DIRECT / CHANGE_REQUEST。 */
@Data
public class InspectionDetailVO {

    private long inspectionId;
    private long penId;
    private String penName;
    private List<String> batchNos;
    private String inspectDate;
    private String inspectStatus;
    private String inspectStatusText;
    private List<ItemResult> itemResults;
    private Boolean isAbnormal;
    private String abnormalSource;
    private String abnormalSummary;
    private Boolean isManualMarked;
    private String manualReason;
    private String manualMarkedByName;
    private String manualMarkedAt;
    private String remark;
    private List<MediaItemVO> media;
    private UserBriefVO registrant;
    private UserBriefVO executor;
    private Boolean isProxy;
    private String registeredAt;
    private String locationStatus;
    private String locationStatusText;
    private Integer distanceM;
    private Boolean isOverdue;
    private Boolean isBackfill;
    private Integer templateVersion;
    private Long reportId;
    private String reportStatus;
    private String editMode;
    private ChangeRequestStateVO changeState;
    private boolean hasRevisions;
    private int version;

    @Data
    public static class ItemResult {
        private long itemId;
        private String itemName;
        private long optionId;
        private String optionName;
        private Boolean optionAbnormal;
        private int sortNo;
    }
}
