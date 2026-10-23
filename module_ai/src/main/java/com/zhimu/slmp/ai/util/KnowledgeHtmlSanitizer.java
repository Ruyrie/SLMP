package com.zhimu.slmp.ai.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识正文 content_html 白名单清洗（技术文档 2.6.6）：只保留 strong、ul、ol、li、br，去除全部属性；
 * script、style 连同内容删除，其余标签只删标签保留文字。结果交给 HtmlCompat.fromHtml。纯 Java。
 */
public final class KnowledgeHtmlSanitizer {

    private static final Pattern DROP_BLOCK = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z0-9]+)[^>]*>");

    private KnowledgeHtmlSanitizer() {
    }

    @NonNull
    public static String sanitize(@Nullable String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = DROP_BLOCK.matcher(html).replaceAll("");
        s = COMMENT.matcher(s).replaceAll("");
        Matcher m = TAG.matcher(s);
        StringBuilder out = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String closing = m.group(1);
            String name = m.group(2).toLowerCase(Locale.ROOT);
            switch (name) {
                case "strong":
                case "ul":
                case "ol":
                case "li":
                    out.append('<').append(closing).append(name).append('>');
                    break;
                case "br":
                    out.append("<br>");
                    break;
                default:
                    break;
            }
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }
}
