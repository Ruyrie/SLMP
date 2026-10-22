package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.today.model.vo.MessageActionVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;

import java.util.Objects;

/**
 * 消息列表中的一行：日期分组标题（今天、昨天、更早），或套用本地覆盖后的消息（已读、邀请处理结果）。
 */
public final class MessageRow {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final int type;
    @Nullable
    private final String header;
    @Nullable
    private final MessageBriefVO message;
    private final boolean read;
    /** 邀请、转让的当前状态（本地覆盖优先） */
    @Nullable
    private final String actionStatus;

    private MessageRow(int type, @Nullable String header, @Nullable MessageBriefVO message, boolean read,
                       @Nullable String actionStatus) {
        this.type = type;
        this.header = header;
        this.message = message;
        this.read = read;
        this.actionStatus = actionStatus;
    }

    @NonNull
    public static MessageRow header(@NonNull String dateGroup) {
        return new MessageRow(TYPE_HEADER, dateGroup, null, true, null);
    }

    @NonNull
    public static MessageRow item(@NonNull MessageBriefVO message, boolean read, @Nullable String actionStatus) {
        return new MessageRow(TYPE_ITEM, null, message, read, actionStatus);
    }

    /** 分组标题文字 */
    @NonNull
    public static String groupTitle(@Nullable String dateGroup) {
        if ("TODAY".equals(dateGroup)) {
            return "今天";
        }
        if ("YESTERDAY".equals(dateGroup)) {
            return "昨天";
        }
        return "更早";
    }

    public int getType() {
        return type;
    }

    public boolean isHeader() {
        return type == TYPE_HEADER;
    }

    /** 日期分组代码 TODAY / YESTERDAY / EARLIER */
    @Nullable
    public String getHeader() {
        return header;
    }

    @Nullable
    public MessageBriefVO getMessage() {
        return message;
    }

    public long getMessageId() {
        return message == null ? -1 : message.getMessageId();
    }

    public boolean isRead() {
        return read;
    }

    @Nullable
    public String getActionStatus() {
        return actionStatus;
    }

    /** 邀请还可以同意或拒绝 */
    public boolean isActionPending() {
        MessageActionVO a = message == null ? null : message.getAction();
        return a != null && STATUS_PENDING.equals(actionStatus) && a.isCanAccept();
    }

    /** 列表比较用的唯一键 */
    @NonNull
    public String key() {
        return isHeader() ? "h_" + header : "m_" + getMessageId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageRow)) {
            return false;
        }
        MessageRow r = (MessageRow) o;
        return type == r.type && read == r.read && Objects.equals(header, r.header)
                && message == r.message && Objects.equals(actionStatus, r.actionStatus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, header, getMessageId(), read, actionStatus);
    }
}
