package com.zhimu.slmp.herd.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.ChangeRequestStateVO;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.common.model.vo.PenBriefVO;
import com.zhimu.slmp.common.model.vo.UserBriefVO;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * API-L19 存栏变动记录详情（L08、L10、L11 编辑模式带入原值）。
 */
public class BatchEventDetailVO {

    private long eventId;
    private long batchId;
    private String eventType;
    private String status;
    private String batchNo;
    private String breedTitle;
    private String eventDate;
    private String createdAt;
    private int quantity;
    private int overCapacityCount;
    private int version;
    private boolean capacityConfirmed;
    private PenBriefVO pen;
    private PenBriefVO toPen;
    private String reasonCode;
    private String remark;
    private String lastEditedAt;
    private UserBriefVO executor;
    private UserBriefVO registrant;
    private UserBriefVO lastEditor;
    private List<MediaItemVO> photos;
    private ChangeRequestStateVO changeState;
    private EntryInfo entry;

    public long getEventId() { return eventId; }

    public long getBatchId() { return batchId; }

    /** ENTRY / TRANSFER / DEATH / SALE */
    public String getEventType() { return eventType; }

    public String getStatus() { return status; }

    public String getBatchNo() { return batchNo; }

    public String getBreedTitle() { return breedTitle; }

    public String getEventDate() { return eventDate; }

    public String getCreatedAt() { return createdAt; }

    public int getQuantity() { return quantity; }

    public int getOverCapacityCount() { return overCapacityCount; }

    public int getVersion() { return version; }

    public boolean isCapacityConfirmed() { return capacityConfirmed; }

    @Nullable
    public PenBriefVO getPen() { return pen; }

    @Nullable
    public PenBriefVO getToPen() { return toPen; }

    /** SICK / CULL / ACCIDENT / OTHER */
    @Nullable
    public String getReasonCode() { return reasonCode; }

    @Nullable
    public String getRemark() { return remark; }

    @Nullable
    public String getLastEditedAt() { return lastEditedAt; }

    @Nullable
    public UserBriefVO getExecutor() { return executor; }

    @Nullable
    public UserBriefVO getRegistrant() { return registrant; }

    @Nullable
    public UserBriefVO getLastEditor() { return lastEditor; }

    public List<MediaItemVO> getPhotos() { return photos == null ? Collections.emptyList() : photos; }

    @Nullable
    public ChangeRequestStateVO getChangeState() { return changeState; }

    /** 仅入栏记录 */
    @Nullable
    public EntryInfo getEntry() { return entry; }

    /** 入栏记录的批次字段 */
    public static class EntryInfo {
        private String source;
        private String purpose;
        private long breedId;
        private String breedName;
        private int entryAgeDays;
        private BigDecimal entryAvgWeight;
        private BigDecimal introUnitPrice;
        private int batchVersion;

        public String getSource() { return source; }

        public String getPurpose() { return purpose; }

        public long getBreedId() { return breedId; }

        public String getBreedName() { return breedName; }

        public int getEntryAgeDays() { return entryAgeDays; }

        @Nullable
        public BigDecimal getEntryAvgWeight() { return entryAvgWeight; }

        /** 管理视图 */
        @Nullable
        public BigDecimal getIntroUnitPrice() { return introUnitPrice; }

        public int getBatchVersion() { return batchVersion; }
    }
}
