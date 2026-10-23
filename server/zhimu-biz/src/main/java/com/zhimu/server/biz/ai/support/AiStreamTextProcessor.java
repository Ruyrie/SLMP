package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.entity.AiCitation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式正文处理：
 * <ul>
 *     <li>截获首行的类型/主题标记（[[NORMAL]]、[[TOPIC:MEDICATION]]），不转发给客户端；</li>
 *     <li>把模型输出的 [^P3] 按首次出现顺序改成 [^1]、[^2]，同一条目同一序号；无效编号删除；</li>
 *     <li>末尾未闭合的 "[^P" 暂缓到下一段，避免把半个标记发给客户端。</li>
 * </ul>
 */
public final class AiStreamTextProcessor {

    private static final Pattern TAG = Pattern.compile("^\\[\\[([A-Z_]+)(?::([A-Z_]+))?]]");
    private static final Pattern P_CITATION = Pattern.compile("\\[\\^P(\\d+)]");
    private static final int MAX_TAG_BUFFER = 96;

    private final List<RetrievalHit> hits;
    private final StringBuilder tagBuffer = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();
    private final Map<Integer, Integer> seqByHit = new LinkedHashMap<>();
    private final List<AiCitation> citations = new ArrayList<>();
    private boolean decided;
    private String answerType = AiCodes.ANSWER_NORMAL;
    private String topic = AiCodes.TOPIC_OTHER;

    public AiStreamTextProcessor(List<RetrievalHit> hits) {
        this.hits = hits == null ? List.of() : hits;
    }

    /** 送入一段增量，返回可发送给客户端的正文 */
    public String feed(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        if (!decided) {
            tagBuffer.append(delta);
            int newline = tagBuffer.indexOf("\n");
            if (newline < 0 && tagBuffer.length() < MAX_TAG_BUFFER) {
                return "";
            }
            String firstLine = newline < 0 ? tagBuffer.toString() : tagBuffer.substring(0, newline);
            String rest = newline < 0 ? "" : tagBuffer.substring(newline + 1);
            tagBuffer.setLength(0);
            decided = true;
            String body = consumeTags(firstLine);
            if (body == null) {
                // 首行不是标记：原样输出
                return renumber(firstLine + (newline < 0 ? "" : "\n") + rest);
            }
            return renumber(body + (body.isEmpty() || rest.isEmpty() ? "" : "\n") + rest);
        }
        return renumber(delta);
    }

    /** 流结束：把缓存的类型标记后正文与未闭合标记一次性输出 */
    public String flush() {
        StringBuilder out = new StringBuilder();
        if (!decided) {
            decided = true;
            String body = consumeTags(tagBuffer.toString());
            tagBuffer.setLength(0);
            if (body != null) {
                out.append(body);
            }
        }
        if (pending.length() > 0) {
            out.append(pending);
            pending.setLength(0);
        }
        return out.toString();
    }

    /**
     * 解析首行开头的标记；返回去掉标记后的正文。
     *
     * @return 首行不是标记时返回 null
     */
    private String consumeTags(String firstLine) {
        String text = firstLine.stripLeading();
        boolean tagged = false;
        while (true) {
            Matcher matcher = TAG.matcher(text);
            if (!matcher.find()) {
                break;
            }
            tagged = true;
            String name = matcher.group(1).toUpperCase(Locale.ROOT);
            String value = matcher.group(2);
            if ("TOPIC".equals(name) && value != null) {
                topic = value.toUpperCase(Locale.ROOT);
            } else {
                answerType = normalizeType(name);
            }
            text = text.substring(matcher.end()).stripLeading();
        }
        return tagged ? text : null;
    }

    private static String normalizeType(String raw) {
        return switch (raw) {
            case AiCodes.ANSWER_NO_BASIS, AiCodes.ANSWER_OFF_TOPIC, AiCodes.ANSWER_BANNED_DRUG,
                 AiCodes.ANSWER_EPIDEMIC -> raw;
            default -> AiCodes.ANSWER_NORMAL;
        };
    }

    private String renumber(String text) {
        pending.append(text);
        String value = pending.toString();
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (true) {
            int start = value.indexOf("[^", index);
            if (start < 0) {
                out.append(value, index, value.length());
                break;
            }
            Matcher matcher = P_CITATION.matcher(value);
            matcher.region(start, value.length());
            if (!matcher.lookingAt()) {
                // 不是 [^P数字]：原样保留；末尾可能是未闭合标记时缓存
                int close = value.indexOf(']', start);
                if (close < 0) {
                    out.append(value, index, start);
                    pending.setLength(0);
                    pending.append(value.substring(start));
                    return out.toString();
                }
                out.append(value, index, close + 1);
                index = close + 1;
                continue;
            }
            out.append(value, index, start);
            int p = Integer.parseInt(matcher.group(1));
            RetrievalHit hit = p >= 1 && p <= hits.size() ? hits.get(p - 1) : null;
            if (hit != null) {
                int seq = seqByHit.computeIfAbsent(p, key -> {
                    int next = seqByHit.size() + 1;
                    citations.add(toCitation(hit, next));
                    return next;
                });
                out.append("[^").append(seq).append(']');
            }
            index = matcher.end();
        }
        pending.setLength(0);
        return out.toString();
    }

    private static AiCitation toCitation(RetrievalHit hit, int seq) {
        AiCitation citation = new AiCitation();
        citation.setSeqNo(seq);
        citation.setKnowledgeId(hit.knowledge().getId());
        citation.setKnowledgeVersionNo(hit.knowledge().getVersionNo());
        citation.setParaKey(hit.paragraph().getParaKey());
        citation.setSourceType(hit.knowledge().getSourceType());
        citation.setIconType(hit.knowledge().getDisplayType());
        citation.setTitleSnapshot(hit.knowledge().getTitle());
        citation.setCategoryNameSnapshot(hit.knowledge().getCategoryName());
        String text = hit.paragraph().getContentText();
        citation.setQuoteText(text != null && text.length() > 1000 ? text.substring(0, 1000) : text);
        return citation;
    }

    public String getAnswerType() {
        return answerType;
    }

    public String getTopic() {
        return topic;
    }

    public List<AiCitation> getCitations() {
        return citations;
    }

    public boolean hasText() {
        return !citations.isEmpty();
    }
}
