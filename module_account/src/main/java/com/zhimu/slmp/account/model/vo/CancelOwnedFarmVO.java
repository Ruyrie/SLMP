package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * 注销清单中本人创建的养殖场：状态、处置方式、存栏、成员数、能否转让。
 */
public class CancelOwnedFarmVO {

    public static final String DISPOSAL_TRANSFER_OR_DISABLE = "TRANSFER_OR_DISABLE";
    public static final String DISPOSAL_WAIT_UNFREEZE = "WAIT_UNFREEZE";
    public static final String DISPOSAL_RETAIN_180_DAYS = "RETAIN_180_DAYS";
    public static final String DISPOSAL_DELETE_NOW = "DELETE_NOW";

    private long farmId;
    @Nullable
    private String name;
    /** NORMAL / FROZEN / DISABLED / PENDING / REJECTED */
    @Nullable
    private String farmStatus;
    @Nullable
    private String farmStatusText;
    @Nullable
    private String disposal;
    @Nullable
    private String disposalText;
    /** 正常、停用时输出 */
    @Nullable
    private Integer currentStock;
    @Nullable
    private String countUnitText;
    private int memberCount;
    private boolean transferable;
    @Nullable
    private String transferPendingToName;

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getFarmStatus() {
        return farmStatus;
    }

    @Nullable
    public String getFarmStatusText() {
        return farmStatusText;
    }

    @Nullable
    public String getDisposal() {
        return disposal;
    }

    @Nullable
    public String getDisposalText() {
        return disposalText;
    }

    @Nullable
    public Integer getCurrentStock() {
        return currentStock;
    }

    @Nullable
    public String getCountUnitText() {
        return countUnitText;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public boolean isTransferable() {
        return transferable;
    }

    @Nullable
    public String getTransferPendingToName() {
        return transferPendingToName;
    }

    /** 须先处理（仍在使用、已冻结） */
    public boolean isBlocking() {
        return DISPOSAL_TRANSFER_OR_DISABLE.equals(disposal) || DISPOSAL_WAIT_UNFREEZE.equals(disposal);
    }
}
