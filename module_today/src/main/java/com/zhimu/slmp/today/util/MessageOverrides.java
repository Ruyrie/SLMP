package com.zhimu.slmp.today.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.today.model.vo.MessageActionVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 消息列表的本地覆盖表（不可变，每次修改返回新对象）：已读集合、已删除集合、邀请处理结果、当前筛选范围是否已全部已读。
 * 本地操作不重新请求；下拉刷新时清空，以服务端为准（技术文档 2.6.6）。
 */
public final class MessageOverrides {

    private static final MessageOverrides EMPTY = new MessageOverrides(
            Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(), false);

    @NonNull
    private final Set<Long> read;
    @NonNull
    private final Set<Long> deleted;
    @NonNull
    private final Map<Long, String> actionStatus;
    private final boolean allRead;

    private MessageOverrides(@NonNull Set<Long> read, @NonNull Set<Long> deleted,
                             @NonNull Map<Long, String> actionStatus, boolean allRead) {
        this.read = read;
        this.deleted = deleted;
        this.actionStatus = actionStatus;
        this.allRead = allRead;
    }

    @NonNull
    public static MessageOverrides empty() {
        return EMPTY;
    }

    @NonNull
    public MessageOverrides markRead(long messageId) {
        if (read.contains(messageId)) {
            return this;
        }
        Set<Long> r = new HashSet<>(read);
        r.add(messageId);
        return new MessageOverrides(Collections.unmodifiableSet(r), deleted, actionStatus, allRead);
    }

    @NonNull
    public MessageOverrides markAllRead() {
        return new MessageOverrides(read, deleted, actionStatus, true);
    }

    @NonNull
    public MessageOverrides delete(long messageId) {
        Set<Long> d = new HashSet<>(deleted);
        d.add(messageId);
        return new MessageOverrides(read, Collections.unmodifiableSet(d), actionStatus, allRead);
    }

    @NonNull
    public MessageOverrides restore(long messageId) {
        if (!deleted.contains(messageId)) {
            return this;
        }
        Set<Long> d = new HashSet<>(deleted);
        d.remove(messageId);
        return new MessageOverrides(read, Collections.unmodifiableSet(d), actionStatus, allRead);
    }

    /** 邀请处理结果：ACCEPTED / REJECTED / EXPIRED；处理后同时标为已读 */
    @NonNull
    public MessageOverrides setActionStatus(long messageId, @NonNull String status) {
        Map<Long, String> a = new HashMap<>(actionStatus);
        a.put(messageId, status);
        return new MessageOverrides(read, deleted, Collections.unmodifiableMap(a), allRead).markRead(messageId);
    }

    public boolean isDeleted(long messageId) {
        return deleted.contains(messageId);
    }

    public boolean isRead(@NonNull MessageBriefVO m) {
        return allRead || m.isRead() || read.contains(m.getMessageId());
    }

    @Nullable
    public String actionStatusOf(@NonNull MessageBriefVO m) {
        String local = actionStatus.get(m.getMessageId());
        if (local != null) {
            return local;
        }
        MessageActionVO a = m.getAction();
        return a == null ? null : a.getStatus();
    }

    /** 套用覆盖得到显示行 */
    @NonNull
    public MessageRow apply(@NonNull MessageBriefVO m) {
        return MessageRow.item(m, isRead(m), actionStatusOf(m));
    }

    @NonNull
    public Set<Long> getDeleted() {
        return deleted;
    }

    public boolean isAllRead() {
        return allRead;
    }
}
