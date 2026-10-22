package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.FileVO;

import java.util.List;

/**
 * 平台通知内容（API-T16 payload，msg_type 为 NOTICE）；已撤回时不返回正文与配图。
 */
public class NoticePayload {

    /** NORMAL / IMPORTANT */
    @Nullable
    private String level;
    @Nullable
    private String title;
    @Nullable
    private String senderName;
    @Nullable
    private String sentAt;
    @Nullable
    private FileVO image;
    @Nullable
    private String content;
    @Nullable
    private List<String> targetFarmNames;
    private boolean revoked;
    @Nullable
    private String revokedAt;

    @Nullable
    public String getLevel() {
        return level;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSenderName() {
        return senderName;
    }

    @Nullable
    public String getSentAt() {
        return sentAt;
    }

    @Nullable
    public FileVO getImage() {
        return image;
    }

    @Nullable
    public String getContent() {
        return content;
    }

    @Nullable
    public List<String> getTargetFarmNames() {
        return targetFarmNames;
    }

    public boolean isRevoked() {
        return revoked;
    }

    @Nullable
    public String getRevokedAt() {
        return revokedAt;
    }
}
