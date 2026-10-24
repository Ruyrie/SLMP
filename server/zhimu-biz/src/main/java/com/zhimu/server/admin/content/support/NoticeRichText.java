package com.zhimu.server.admin.content.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 平台通知正文清洗（管理端-02 C01）：只保留段落、换行、加粗、编号列表、项目列表，去掉全部标签属性与脚本。
 * 字数按去标签后的纯文字计（不把 HTML 实体与标签算进去）。
 */
public final class NoticeRichText {

    public static final int TITLE_MAX = 50;
    public static final int CONTENT_MAX = 2000;

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern DANGEROUS = Pattern.compile("(?is)<(script|style|iframe|object|embed)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern COMMENT = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern ALL_TAGS = Pattern.compile("(?is)</?[a-zA-Z][^>]*>");
    private static final Set<String> ALLOWED = Set.of("p", "br", "strong", "ol", "ul", "li");

    private NoticeRichText() {
    }

    /** 清洗为受限 HTML；不允许的标签只去掉标签、保留其文字（脚本与样式连同内容删除） */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String source = COMMENT.matcher(DANGEROUS.matcher(raw).replaceAll("")).replaceAll("");
        StringBuilder out = new StringBuilder(source.length());
        Deque<String> stack = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(source);
        int last = 0;
        while (matcher.find()) {
            out.append(source, last, matcher.start());
            String token = matcher.group();
            last = matcher.end();
            String inner = token.substring(1, token.length() - 1).trim();
            boolean closing = inner.startsWith("/");
            if (closing) {
                inner = inner.substring(1).trim();
            }
            String name = tagName(inner);
            String canonical = canonical(name);
            if (canonical == null) {
                continue;
            }
            if ("br".equals(canonical)) {
                if (!closing) {
                    out.append("<br>");
                }
                continue;
            }
            if (closing) {
                if (stack.contains(canonical)) {
                    while (!stack.isEmpty()) {
                        String open = stack.pop();
                        out.append("</").append(open).append('>');
                        if (open.equals(canonical)) {
                            break;
                        }
                    }
                }
                continue;
            }
            out.append('<').append(canonical).append('>');
            stack.push(canonical);
        }
        out.append(source, last, source.length());
        while (!stack.isEmpty()) {
            out.append("</").append(stack.pop()).append('>');
        }
        return out.toString().trim();
    }

    /** 去标签后的纯文字（换行保留），用于字数与消息摘要 */
    public static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n")
                .replaceAll("(?i)<[^>]*>", "");
        text = unescape(text).replace('\u00a0', ' ');
        return text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** 去标签后的字符数（按 Unicode 码点） */
    public static int contentLength(String html) {
        String plain = plainText(html);
        return plain.isEmpty() ? 0 : plain.codePointCount(0, plain.length());
    }

    /** 正文前 n 个字符作为消息摘要（换行替换为空格） */
    public static String summary(String html, int max) {
        String plain = plainText(html).replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (plain.codePointCount(0, plain.length()) <= max) {
            return plain;
        }
        return plain.substring(0, plain.offsetByCodePoints(0, max)) + "…";
    }

    private static String tagName(String inner) {
        int end = 0;
        while (end < inner.length()) {
            char c = inner.charAt(end);
            if (Character.isWhitespace(c) || c == '/' || c == '>') {
                break;
            }
            end++;
        }
        return inner.substring(0, end).toLowerCase();
    }

    private static String canonical(String name) {
        return switch (name) {
            case "p", "div" -> "p";
            case "br" -> "br";
            case "b", "strong" -> "strong";
            case "ol" -> "ol";
            case "ul" -> "ul";
            case "li" -> "li";
            default -> ALLOWED.contains(name) ? name : null;
        };
    }

    private static String unescape(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }
}
