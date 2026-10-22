package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;

/**
 * 从模型回答中取出 JSON 对象：去掉 ```json 代码块标记、前后的说明文字，按括号配对截取第一个完整对象；
 * 宽松解析（允许尾逗号等）。取不到时返回 null，由调用方兜底。
 */
public final class AiJson {

    private AiJson() {
    }

    @Nullable
    public static JsonObject extractObject(@Nullable String content) {
        if (content == null) {
            return null;
        }
        String text = stripFence(content.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int end = matchingBrace(text, start);
        String candidate = end > start ? text.substring(start, end + 1) : text.substring(start);
        JsonObject obj = parseLenient(candidate);
        if (obj == null && end < 0) {
            obj = repairTruncated(candidate);
        }
        return obj;
    }

    /** 最多回退的次数（每次退到上一个结构逗号） */
    static final int MAX_REPAIR_STEPS = 8;

    /**
     * 回答被截断：补齐引号和括号后解析；仍失败（停在键名、冒号后）就退到上一个结构逗号再试。
     */
    @Nullable
    static JsonObject repairTruncated(@NonNull String candidate) {
        String cut = candidate;
        for (int i = 0; i < MAX_REPAIR_STEPS && cut.length() > 1; i++) {
            JsonObject obj = parseLenient(cut + closers(cut));
            if (obj != null) {
                return obj;
            }
            int comma = lastStructuralComma(cut);
            if (comma <= 0) {
                return null;
            }
            cut = cut.substring(0, comma);
        }
        return null;
    }

    /** 字符串外最后一个逗号的下标；没有返回 -1 */
    static int lastStructuralComma(@NonNull String text) {
        int last = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == ',') {
                last = i;
            }
        }
        return last;
    }

    /** 去掉 Markdown 代码块标记 */
    @NonNull
    static String stripFence(@NonNull String text) {
        String t = text;
        int fence = t.indexOf("```");
        if (fence >= 0) {
            int lineEnd = t.indexOf('\n', fence);
            int close = t.indexOf("```", lineEnd < 0 ? fence + 3 : lineEnd);
            if (lineEnd >= 0) {
                t = close > lineEnd ? t.substring(lineEnd + 1, close) : t.substring(lineEnd + 1);
            }
        }
        return t.trim();
    }

    /** 与 start 处的 { 配对的 } 下标（忽略字符串中的括号）；没有配对返回 -1 */
    static int matchingBrace(@NonNull String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 截断时需要补上的括号（按未闭合的 { [ 逆序） */
    @NonNull
    static String closers(@NonNull String text) {
        StringBuilder stack = new StringBuilder();
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                stack.append(c);
            } else if ((c == '}' || c == ']') && stack.length() > 0) {
                stack.setLength(stack.length() - 1);
            }
        }
        StringBuilder out = new StringBuilder(inString ? "\"" : "");
        for (int i = stack.length() - 1; i >= 0; i--) {
            out.append(stack.charAt(i) == '{' ? '}' : ']');
        }
        return out.toString();
    }

    @Nullable
    private static JsonObject parseLenient(@NonNull String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(com.google.gson.Strictness.LENIENT);
            JsonElement e = JsonParser.parseReader(reader);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (JsonSyntaxException | IllegalStateException | StackOverflowError e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
