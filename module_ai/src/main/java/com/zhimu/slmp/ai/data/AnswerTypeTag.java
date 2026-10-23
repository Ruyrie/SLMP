package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.model.AiCodes;

import java.util.Locale;

/**
 * 测试直连时识别回答类型：提示词要求模型第一行只输出 [[NORMAL]]、[[NO_BASIS]]、[[OFF_TOPIC]]、[[BANNED_DRUG]]、[[EPIDEMIC]]
 * 之一，正文从第二行开始。流式到达时先缓存开头，确定类型后去掉标记再输出正文；开头不是标记（或 24 个字符内没有闭合）
 * 时按 NORMAL 原样输出。纯 Java，单元测试覆盖。
 */
public final class AnswerTypeTag {

    static final int MAX_PREFIX = 24;

    private final StringBuilder pending = new StringBuilder();
    private boolean decided;
    @NonNull
    private String type = AiCodes.ANSWER_NORMAL;

    /** 送入一段正文增量，返回可以显示的部分（类型未确定前为空串） */
    @NonNull
    public String feed(@NonNull String delta) {
        if (decided) {
            return delta;
        }
        pending.append(delta);
        String lead = stripLeading(pending.toString());
        if (lead.isEmpty()) {
            return "";
        }
        if (!lead.startsWith("[[")) {
            if (lead.equals("[")) {
                return "";
            }
            decided = true;
            return lead;
        }
        int end = lead.indexOf("]]");
        if (end < 0) {
            if (lead.length() > MAX_PREFIX) {
                decided = true;
                return lead;
            }
            return "";
        }
        type = normalize(lead.substring(2, end));
        decided = true;
        return stripLeading(lead.substring(end + 2));
    }

    /** 流结束：类型还没确定时把缓存原样输出 */
    @NonNull
    public String flush() {
        if (decided) {
            return "";
        }
        decided = true;
        return stripLeading(pending.toString());
    }

    @NonNull
    public String getType() {
        return type;
    }

    /** 完整回答：返回 {类型, 去掉标记后的正文} */
    @NonNull
    public static String[] split(@NonNull String full) {
        AnswerTypeTag t = new AnswerTypeTag();
        String body = t.feed(full) + t.flush();
        return new String[]{t.getType(), body.trim()};
    }

    @NonNull
    static String normalize(@NonNull String raw) {
        String tag = raw.trim().toUpperCase(Locale.ROOT);
        switch (tag) {
            case AiCodes.ANSWER_NO_BASIS:
            case AiCodes.ANSWER_OFF_TOPIC:
            case AiCodes.ANSWER_BANNED_DRUG:
            case AiCodes.ANSWER_EPIDEMIC:
                return tag;
            default:
                return AiCodes.ANSWER_NORMAL;
        }
    }

    @NonNull
    private static String stripLeading(@NonNull String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }
}
