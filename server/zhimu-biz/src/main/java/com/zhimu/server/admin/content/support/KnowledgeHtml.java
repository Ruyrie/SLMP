package com.zhimu.server.admin.content.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识正文解析与段落白名单。
 * <p>
 * 编辑器正文为受限 HTML：小标题、段落、引用、列表、分隔线、图片、课堂视频卡与 https 链接。
 * 保存 / 发布时按块拆成段落：小标题、段落、引用各为一段，一个列表整体为一段，图片、视频卡各为一段；
 * 每段另存 content_html（只允许加粗、编号列表、项目列表、列表项、换行）与 content_text。
 * 段落键 data-para-key 缺失、重复或不属于本条目历史版本时重新生成（12 位小写字母数字）。
 */
public final class KnowledgeHtml {

    public static final int MAX_TEXT_LENGTH = 20000;
    public static final int MAX_KEY_LENGTH = 32;

    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern OPEN = Pattern.compile(
            "(?is)<(h[1-6]|p|blockquote|ul|ol|div|img)\\b([^>]*)>");
    private static final Pattern KEY_ATTR = Pattern.compile("(?is)data-para-key\\s*=\\s*[\"']([^\"']{1,32})[\"']");
    private static final Pattern ATTR = Pattern.compile("(?is)([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern TAG = Pattern.compile("(?is)<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>");
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style|iframe|object|embed)[^>]*>.*?</\\1>");
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_-]{1,32}");

    private KnowledgeHtml() {
    }

    /**
     * 解析正文并生成规范化的受限 HTML。
     *
     * @param html        编辑器正文
     * @param allowedKeys 本条目历史版本出现过的段落键（null 表示不限制）
     */
    public static Parsed parse(String html, Set<String> allowedKeys) {
        String cleaned = html == null ? "" : COMMENT.matcher(SCRIPT.matcher(html).replaceAll("")).replaceAll("");
        List<Paragraph> paragraphs = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        Matcher matcher = OPEN.matcher(cleaned);
        int searchFrom = 0;
        while (searchFrom < cleaned.length() && matcher.find(searchFrom)) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            String attrs = matcher.group(2) == null ? "" : matcher.group(2);
            int contentStart = matcher.end();
            String inner = "";
            int next = contentStart;
            if (!"img".equals(tag)) {
                int close = indexOfIgnoreCase(cleaned, "</" + tag + ">", contentStart);
                if (close >= 0) {
                    inner = cleaned.substring(contentStart, close);
                    next = close + tag.length() + 3;
                }
            }
            String key = uniqueKey(attr(attrs, "data-para-key"), used, allowedKeys);
            Paragraph paragraph = block(tag, attrs, inner, key);
            if (paragraph != null) {
                paragraphs.add(paragraph);
            }
            searchFrom = Math.max(next, matcher.end());
            matcher = OPEN.matcher(cleaned);
        }
        if (paragraphs.isEmpty()) {
            String plain = plainText(cleaned);
            if (!plain.isBlank()) {
                for (String line : plain.split("\\n+")) {
                    String text = line.trim();
                    if (!text.isEmpty()) {
                        paragraphs.add(new Paragraph(newKey(used), "TEXT", text, text, null, null, null));
                    }
                }
            }
        }
        String content = render(paragraphs);
        String contentText = textOf(paragraphs);
        return new Parsed(content, contentText, paragraphs);
    }

    private static Paragraph block(String tag, String attrs, String inner, String key) {
        if ("img".equals(tag)) {
            Long fileId = longAttr(attrs, "data-file-id");
            String src = attr(attrs, "src");
            String url = safeUrl(src);
            return new Paragraph(key, "IMAGE", null, null, null, fileId, url);
        }
        if ("div".equals(tag) && (hasAttr(attrs, "data-course-id") || "video".equalsIgnoreCase(attr(attrs, "data-type")))) {
            Long courseId = longAttr(attrs, "data-course-id");
            return new Paragraph(key, "VIDEO", null, null, courseId, null, null);
        }
        String type = switch (tag.charAt(0)) {
            case 'h' -> "HEADING";
            case 'b' -> "QUOTE";
            case 'u', 'o' -> "LIST";
            default -> "TEXT";
        };
        if ("blockquote".equals(tag)) {
            type = "QUOTE";
        }
        String safe = inline(inner);
        String text = plainText(safe);
        if (text.isBlank() && (safe == null || safe.isBlank())) {
            return null;
        }
        return new Paragraph(key, type, safe, text, null, null, null);
    }

    /** 由段落重新拼接受限 HTML（发布时写入版本与段落）。 */
    public static String render(List<Paragraph> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Paragraph p : paragraphs) {
            String key = escapeAttr(p.paraKey());
            switch (p.paraType()) {
                case "HEADING" -> sb.append("<h2 data-para-key=\"").append(key).append("\">")
                        .append(nullToEmpty(p.contentHtml())).append("</h2>");
                case "QUOTE" -> sb.append("<blockquote data-para-key=\"").append(key).append("\">")
                        .append(nullToEmpty(p.contentHtml())).append("</blockquote>");
                case "LIST" -> sb.append("<ul data-para-key=\"").append(key).append("\">")
                        .append(nullToEmpty(p.contentHtml())).append("</ul>");
                case "IMAGE" -> {
                    sb.append("<img data-para-key=\"").append(key).append("\"");
                    if (p.imageFileId() != null) {
                        sb.append(" data-file-id=\"").append(p.imageFileId()).append("\"");
                    }
                    if (p.imageUrl() != null) {
                        sb.append(" src=\"").append(escapeAttr(p.imageUrl())).append("\"");
                    }
                    sb.append("/>");
                }
                case "VIDEO" -> sb.append("<div data-type=\"video\" data-para-key=\"").append(key)
                        .append("\" data-course-id=\"").append(p.courseId() == null ? "" : p.courseId())
                        .append("\"></div>");
                default -> sb.append("<p data-para-key=\"").append(key).append("\">")
                        .append(nullToEmpty(p.contentHtml())).append("</p>");
            }
        }
        return sb.toString();
    }

    public static String textOf(List<Paragraph> paragraphs) {
        StringBuilder sb = new StringBuilder();
        for (Paragraph p : paragraphs) {
            if (p.contentText() == null || p.contentText().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(p.contentText());
        }
        return sb.toString();
    }

    /** 正文纯文字（版本对比、校验字数用）。 */
    public static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String value = SCRIPT.matcher(html).replaceAll("");
        value = value.replaceAll("(?i)<br\\s*/?>", "\n");
        value = value.replaceAll("(?i)</(p|div|h[1-6]|li|blockquote|ul|ol)>", "\n");
        value = TAG.matcher(value).replaceAll("");
        value = unescape(value).replace('\u00a0', ' ');
        StringBuilder out = new StringBuilder();
        for (String line : value.split("\\n")) {
            String trimmed = line.replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
            if (!trimmed.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(trimmed);
            }
        }
        return out.toString();
    }

    /** 取出正文里出现过的段落键，用于判断复制粘贴来的键。 */
    public static Set<String> extractKeys(String content) {
        Set<String> keys = new HashSet<>();
        if (content == null || content.isBlank()) {
            return keys;
        }
        Matcher matcher = KEY_ATTR.matcher(content);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** 段落内白名单：只保留加粗、编号列表、项目列表、列表项、换行。 */
    public static String inline(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String value = SCRIPT.matcher(html).replaceAll("");
        Matcher matcher = TAG.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            String closing = matcher.group(1);
            String replacement = switch (name) {
                case "strong" -> "<" + closing + "strong>";
                case "b" -> "<" + closing + "strong>";
                case "ul", "ol", "li" -> "<" + closing + name + ">";
                case "br" -> "<br>";
                default -> "";
            };
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    private static String uniqueKey(String candidate, Set<String> used, Set<String> allowedKeys) {
        String key = candidate == null ? null : candidate.trim();
        boolean valid = key != null && VALID_KEY.matcher(key).matches()
                && !used.contains(key)
                && (allowedKeys == null || allowedKeys.contains(key));
        if (!valid) {
            do {
                key = newKey(used);
            } while (used.contains(key));
        }
        used.add(key);
        return key;
    }

    private static String newKey(Set<String> used) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(KEY_CHARS.charAt(random.nextInt(KEY_CHARS.length())));
            }
            String key = sb.toString();
            if (used == null || !used.contains(key)) {
                return key;
            }
        }
        return "p" + System.nanoTime();
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        int max = text.length() - needle.length();
        for (int i = Math.max(from, 0); i <= max; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasAttr(String attrs, String name) {
        return attr(attrs, name) != null;
    }

    private static String attr(String attrs, String name) {
        if (attrs == null) {
            return null;
        }
        Matcher matcher = ATTR.matcher(attrs);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(name)) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private static Long longAttr(String attrs, String name) {
        String value = attr(attrs, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        return value.startsWith("https://") ? value : null;
    }

    private static String unescape(String value) {
        return value.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    private static String escapeAttr(String value) {
        return nullToEmpty(value).replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 一个受限度落。content_html 已按段落白名单清洗；图片、视频段为空。 */
    public record Paragraph(String paraKey, String paraType, String contentHtml, String contentText,
                            Long courseId, Long imageFileId, String imageUrl) {

        public Paragraph withText(String text) {
            return new Paragraph(paraKey, paraType, contentHtml, text, courseId, imageFileId, imageUrl);
        }

        public Paragraph withImageUrl(String url) {
            return new Paragraph(paraKey, paraType, contentHtml, contentText, courseId, imageFileId, url);
        }
    }

    /** 解析结果：规范化的受限 HTML、纯文字与段落列表。 */
    public record Parsed(String content, String contentText, List<Paragraph> paragraphs) {
    }
}
