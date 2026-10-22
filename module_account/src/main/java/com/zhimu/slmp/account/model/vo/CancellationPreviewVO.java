package com.zhimu.slmp.account.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * API-A19 注销预览：个人数据说明、本人创建的养殖场与处置、将要退出的养殖场、能否注销与清单版本。
 */
public class CancellationPreviewVO {

    @Nullable
    private String phoneMasked;
    /** 消息、AI 问答、待办设置、恢复码 */
    @Nullable
    private String personalDataDesc;
    @Nullable
    private String phoneReleaseDate;
    @Nullable
    private String checklistVersion;
    private boolean canCancel;
    @Nullable
    private List<String> blockReasons;
    /** 找回后 7 天内输出 */
    @Nullable
    private String cancelAvailableDate;
    /** 有已停用的养殖场时输出 */
    @Nullable
    private String farmCleanupDate;
    @Nullable
    private List<CancelOwnedFarmVO> ownedFarms;
    private int transferableFarmCount;
    @Nullable
    private List<CancelExitFarmVO> exitFarms;

    @Nullable
    public String getPhoneMasked() {
        return phoneMasked;
    }

    @Nullable
    public String getPersonalDataDesc() {
        return personalDataDesc;
    }

    @Nullable
    public String getPhoneReleaseDate() {
        return phoneReleaseDate;
    }

    @Nullable
    public String getChecklistVersion() {
        return checklistVersion;
    }

    public boolean isCanCancel() {
        return canCancel;
    }

    @NonNull
    public List<String> getBlockReasons() {
        return blockReasons == null ? Collections.emptyList() : blockReasons;
    }

    @Nullable
    public String getCancelAvailableDate() {
        return cancelAvailableDate;
    }

    @Nullable
    public String getFarmCleanupDate() {
        return farmCleanupDate;
    }

    @NonNull
    public List<CancelOwnedFarmVO> getOwnedFarms() {
        return ownedFarms == null ? Collections.emptyList() : ownedFarms;
    }

    public int getTransferableFarmCount() {
        return transferableFarmCount;
    }

    @NonNull
    public List<CancelExitFarmVO> getExitFarms() {
        return exitFarms == null ? Collections.emptyList() : exitFarms;
    }
}
