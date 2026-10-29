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
 *     <li>把模型输出的 [^P3]（及 [P3]、[^p3]、全角括号、多编号等变体）按首次出现顺序改成客户端认的 [^1]、[^2]，
 *     同一条目同一序号；无效编号删除；</li>
 *     <li>末尾未闭合的标记（含只到 "[" 的半个）暂缓到下一段，避免把半个标记发给客户端。</li>
 * </ul>
 */
public final class AiStreamTextProcessor {

    private static final Pattern TAG = Pattern.compile("^\\[\\[([A-Z_]+)(?::([A-Z_]+))?]]");
    /** 一个编号：可带 ^、P 前缀，例如 ^P3、P3、^3 */
    private static final String CITE_ITEM = "(?:\\^\\s*[Pp]?|[Pp])\\s*\\d{1,3}";
    /** 完整引用标记（半角 / 全角括号，括号内可有多个编号，以逗号、顿号分隔） */
    private static final Pattern CITATION = Pattern.compile(
            "[\\[［【]\\s*" + CITE_ITEM + "(?:\\s*[,，、]\\s*\\^?\\s*[Pp]?\\s*\\d{1,3})*\\s*[\\]］】]");
    private static final Pattern CITATION_NUMBER = Pattern.compile("\\d{1,3}");
    /** 还没写完的引用标记：左括号开头、只含标记可能出现的字符 */
    private static final Pattern PARTIAL_CITATION = Pattern.compile("[\\[［【][\\s^Pp\\d,，、]*");
    private static final int MAX_PARTIAL = 24;
    private static final int MAX_TAG_BUFFER = 96;

    private final List<RetrievalHit> hits;
    private final StringBuilder tagBuffer = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder answerPrefix = new StringBuilder();
    private boolean prefixDecided;
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
                return cleanStart(renumber(firstLine + (newline < 0 ? "" : "\n") + rest), false);
            }
            return cleanStart(renumber(body + (body.isEmpty() || rest.isEmpty() ? "" : "\n") + rest), false);
        }
        return cleanStart(renumber(delta), false);
    }

    /** 流结束：把缓存的类型标记后正文与未闭合标记一次性输出 */
    public String flush() {
        StringBuilder out = new StringBuilder();
        if (!decided) {
            decided = true;
            String buffered = tagBuffer.toString();
            String body = consumeTags(buffered);
            tagBuffer.setLength(0);
            out.append(renumber(body == null ? buffered : body));
        }
        out.append(finishPending());
        return cleanStart(out.toString(), true);
    }

    private String cleanStart(String text, boolean finished) {
        if (prefixDecided) {
            return text;
        }
        answerPrefix.append(text);
        String candidate = answerPrefix.toString().stripLeading();
        if (!finished && (AiCodes.NO_BASIS_LEAD.startsWith(candidate)
                || ("**" + AiCodes.NO_BASIS_LEAD + "**").startsWith(candidate))) {
            return "";
        }
        prefixDecided = true;
        answerPrefix.setLength(0);
        return AiText.answerBody(candidate);
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

    /**
     * 引用标记统一改成客户端认的 [^n]。模型实际会写出的变体都按资料编号处理：[^P3]、[^p3]、[^ P3]、[P3]、[^3]、
     * 全角［^P3］【^P3】、一个括号里多个编号 [^P1, P3]（拆成 [^1][^2]）。末尾可能还没写完的标记（含只到“[”就被
     * 切开的增量，如“…疫病 [”+“^P3]。”）先缓存，等下一段再判断，避免把半个标记原样发给客户端。
     */
    private String renumber(String text) {
        pending.append(text);
        String value = pending.toString();
        pending.setLength(0);
        int hold = partialStart(value);
        if (hold >= 0) {
            pending.append(value.substring(hold));
            value = value.substring(0, hold);
        }
        return replaceCitations(value);
    }

    private String replaceCitations(String value) {
        Matcher matcher = CITATION.matcher(value);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            out.append(value, index, matcher.start());
            Matcher number = CITATION_NUMBER.matcher(matcher.group());
            while (number.find()) {
                out.append(citationMark(Integer.parseInt(number.group())));
            }
            index = matcher.end();
        }
        out.append(value, index, value.length());
        return out.toString();
    }

    /** 资料编号 p 对应的 [^n]；无效编号返回空串（删除） */
    private String citationMark(int p) {
        RetrievalHit hit = p >= 1 && p <= hits.size() ? hits.get(p - 1) : null;
        if (hit == null) {
            return "";
        }
        int seq = seqByHit.computeIfAbsent(p, key -> {
            int next = seqByHit.size() + 1;
            citations.add(toCitation(hit, next));
            return next;
        });
        return "[^" + seq + "]";
    }

    /** 末尾从某个左括号起可能是未写完的引用标记时返回该位置，否则 -1 */
    private static int partialStart(String value) {
        int open = -1;
        for (int i = value.length() - 1; i >= 0 && value.length() - i <= MAX_PARTIAL; i--) {
            char c = value.charAt(i);
            if (c == '[' || c == '［' || c == '【') {
                open = i;
                break;
            }
        }
        if (open < 0) {
            return -1;
        }
        return PARTIAL_CITATION.matcher(value.substring(open)).matches() ? open : -1;
    }

    /** 流结束时缓存的半个标记：有编号的按已闭合处理，否则原样输出 */
    private String finishPending() {
        String tail = pending.toString();
        pending.setLength(0);
        if (tail.isEmpty()) {
            return tail;
        }
        if (PARTIAL_CITATION.matcher(tail).matches() && CITATION_NUMBER.matcher(tail).find()) {
            return replaceCitations(tail.stripTrailing() + "]");
        }
        return tail;
    }

    private static AiCitation toCitation(RetrievalHit hit, int seq) {
        AiCitation citation = new AiCitation();
        citation.setSeqNo(seq);
        citation.setKnowledgeId(hit.knowledge().getId());
        citation.setKnowledgeVersionNo(hit.knowledge().getVersionNo() == null ? 1 : hit.knowledge().getVersionNo());
        citation.setParaKey(hit.paragraph().getParaKey() == null ? "" : hit.paragraph().getParaKey());
        citation.setSourceType(hit.knowledge().getSourceType() == null ? AiCodes.SOURCE_PLATFORM
                : hit.knowledge().getSourceType());
        citation.setIconType(hit.knowledge().getDisplayType() == null ? AiCodes.ICON_STANDARD
                : hit.knowledge().getDisplayType());
        citation.setTitleSnapshot(hit.knowledge().getTitle() == null ? "" : hit.knowledge().getTitle());
        citation.setCategoryNameSnapshot(hit.knowledge().getCategoryName() == null ? ""
                : hit.knowledge().getCategoryName());
        String text = hit.paragraph().getContentText();
        citation.setQuoteText(text != null && text.length() > 1000 ? text.substring(0, 1000)
                : (text == null ? "" : text));
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
