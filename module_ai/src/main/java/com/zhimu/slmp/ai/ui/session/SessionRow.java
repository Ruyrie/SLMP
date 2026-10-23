package com.zhimu.slmp.ai.ui.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;

import java.util.Objects;

/**
 * K02 列表行：分组标题（“今天”“昨天”“近 7 天”“更早”，搜索时不分组）或会话项。
 */
public final class SessionRow {

    @Nullable
    public final String header;
    @Nullable
    public final AiSessionItemVO item;

    private SessionRow(@Nullable String header, @Nullable AiSessionItemVO item) {
        this.header = header;
        this.item = item;
    }

    @NonNull
    public static SessionRow header(@NonNull String text) {
        return new SessionRow(text, null);
    }

    @NonNull
    public static SessionRow of(@NonNull AiSessionItemVO item) {
        return new SessionRow(null, item);
    }

    public boolean isHeader() {
        return item == null;
    }

    @Nullable
    public String group() {
        return item == null ? null : item.getDateGroup();
    }

    @NonNull
    public String key() {
        return item == null ? "h_" + header : "s_" + item.getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionRow)) {
            return false;
        }
        SessionRow r = (SessionRow) o;
        if (item == null || r.item == null) {
            return item == r.item && Objects.equals(header, r.header);
        }
        return item.getId() == r.item.getId() && item.getRoundCount() == r.item.getRoundCount()
                && Objects.equals(item.getLastAskedAt(), r.item.getLastAskedAt())
                && Objects.equals(item.getTitle(), r.item.getTitle());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }
}
