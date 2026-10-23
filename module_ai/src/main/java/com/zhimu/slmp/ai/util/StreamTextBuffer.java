package com.zhimu.slmp.ai.util;

import androidx.annotation.NonNull;

/**
 * 流式正文缓冲（技术文档 2.6.1 第 10 步）：累积 delta 原文；显示时去掉完整的 [^n]，末尾未闭合的“[”“[^数字”暂缓到下一段，
 * 去掉开头空白。纯 Java。
 */
public final class StreamTextBuffer {

    private final StringBuilder raw = new StringBuilder();

    public void append(@NonNull String delta) {
        raw.append(delta);
    }

    public void clear() {
        raw.setLength(0);
    }

    /** 原文（含 [^n]） */
    @NonNull
    public String raw() {
        return raw.toString();
    }

    /** 用于显示的文字 */
    @NonNull
    public String visible() {
        String s = AiText.streamingVisible(raw.toString());
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    /** 是否已有可显示的文字 */
    public boolean hasText() {
        return !visible().trim().isEmpty();
    }
}
