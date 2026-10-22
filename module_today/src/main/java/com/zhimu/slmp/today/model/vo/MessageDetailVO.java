package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

/**
 * 消息详情（API-T16 data）：MessageBriefVO + 平铺的 OpenResultVO 字段 + payload（按 msg_type 由 MessageRepository 转换）。
 */
public class MessageDetailVO extends MessageBriefVO {

    @Nullable
    private String targetStatus;
    @Nullable
    private String targetStatusText;
    @Nullable
    private LinkVO link;
    @Nullable
    private String deletedRecordType;
    @Nullable
    private String pageTitle;
    @Nullable
    private String deletedTitle;
    @Nullable
    private String deletedDesc;
    @Nullable
    private String denyTitle;
    @Nullable
    private String denyDesc;
    private int totalUnread;
    @Nullable
    private JsonObject payload;

    /** MessageRepository 按 msg_type 转换后的内容（不参与 JSON 解析） */
    @Nullable
    private transient NoticePayload notice;
    @Nullable
    private transient TransferPayload transfer;

    @Nullable
    public String getTargetStatus() {
        return targetStatus;
    }

    @Nullable
    public String getTargetStatusText() {
        return targetStatusText;
    }

    @Nullable
    public LinkVO getLink() {
        return link;
    }

    @Nullable
    public String getDeletedRecordType() {
        return deletedRecordType;
    }

    @Nullable
    public String getPageTitle() {
        return pageTitle;
    }

    @Nullable
    public String getDeletedTitle() {
        return deletedTitle;
    }

    @Nullable
    public String getDeletedDesc() {
        return deletedDesc;
    }

    @Nullable
    public String getDenyTitle() {
        return denyTitle;
    }

    @Nullable
    public String getDenyDesc() {
        return denyDesc;
    }

    public int getTotalUnread() {
        return totalUnread;
    }

    @Nullable
    public JsonObject getPayload() {
        return payload;
    }

    @Nullable
    public NoticePayload getNotice() {
        return notice;
    }

    @Nullable
    public TransferPayload getTransfer() {
        return transfer;
    }

    public void setNotice(@Nullable NoticePayload notice) {
        this.notice = notice;
    }

    public void setTransfer(@Nullable TransferPayload transfer) {
        this.transfer = transfer;
    }

    /** payload 中的说明文字（FARM_FROZEN 等，取 text / reason） */
    @Nullable
    public String payloadString(String key) {
        if (payload == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return null;
        }
        return payload.get(key).getAsString();
    }
}
