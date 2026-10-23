package com.zhimu.server.biz.herd.vo;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.vo.ChangeRequestStateVO;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.common.vo.PenBriefVO;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** API-L19 存栏变动记录详情（L08、L10、L11 编辑模式带入原值）。 */
@Data
public class BatchEventDetailVO {

    private Long eventId;
    private Long batchId;
    /** ENTRY / TRANSFER / DEATH / SALE */
    private String eventType;
    private String status;
    private String batchNo;
    private String breedTitle;
    private String eventDate;
    private String createdAt;
    private Integer quantity;
    private Integer overCapacityCount;
    private Integer version;
    private Boolean capacityConfirmed;
    private PenBriefVO pen;
    private PenBriefVO toPen;
    /** SICK / CULL / ACCIDENT / OTHER */
    private String reasonCode;
    private String remark;
    private String lastEditedAt;
    private UserBriefVO executor;
    private UserBriefVO registrant;
    private UserBriefVO lastEditor;
    private List<MediaItemVO> photos;
    private ChangeRequestStateVO changeState;
    /** 仅入栏记录 */
    private EntryInfo entry;

    /** 入栏记录的批次字段 */
    @Data
    public static class EntryInfo {
        private String source;
        private String purpose;
        private Long breedId;
        private String breedName;
        private Integer entryAgeDays;
        private BigDecimal entryAvgWeight;
        @ManagerView
        private BigDecimal introUnitPrice;
        private Integer batchVersion;
    }
}
