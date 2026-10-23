package com.zhimu.server.biz.ai.support;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识正文 HTML 白名单清洗：只保留 strong、ul、ol、li、br，其余标签与全部属性去除。
 */
public final class AiHtmlSanitizer {

    private static final Set<String> ALLOWED = Set.of("strong", "ul", "ol", "li", "br");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");

    private AiHtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = SCRIPT.matcher(html).replaceAll("");
        Matcher matcher = TAG.matcher(cleaned);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase();
            String replacement = ALLOWED.contains(name)
                    ? "<" + matcher.group(1) + name + ("br".equals(name) ? "/" : "") + ">"
                    : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
