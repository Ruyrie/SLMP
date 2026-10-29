package com.zhimu.slmp.ai.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.LinkedBatchVO;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.base.util.DateFormatter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模块11 文案（UI 文档 2.5 节）与格式化，纯 Java，单元测试覆盖。
 */
public final class AiText {

    private static final String LEGACY_SOURCE_PREFIX = "未找到平台知识依据，仅供参考。";

    /** Move only the old, fixed source banner out of historical answer bodies; preserve all substantive caveats. */
    @NonNull
    public static String answerBody(@NonNull AiMessageVO message) {
        String body = message.getAnswerText() == null ? "" : message.getAnswerText().trim();
        if (AiCodes.ANSWER_NO_BASIS.equals(message.getAnswerType()) && body.startsWith(LEGACY_SOURCE_PREFIX)) {
            body = body.substring(LEGACY_SOURCE_PREFIX.length()).trim();
        }
        return body;
    }

    @NonNull
    public static String sourceNote(@NonNull AiMessageVO message) {
        if (message.getSourceNote() != null && !message.getSourceNote().trim().isEmpty()) {
            return message.getSourceNote();
        }
        return AiCodes.ANSWER_NO_BASIS.equals(message.getAnswerType())
                ? "本次回答缺少平台知识依据，请结合实际情况核实。" : "";
    }

    public static final int MAX_QUESTION = 500;
    public static final int COUNT_FROM = 480;
    public static final int MAX_IMAGES = 9;
    public static final int MAX_SESSION_KEYWORD = 50;
    /** 会话标题上限（服务端 40 字） */
    public static final int MAX_SESSION_TITLE = 40;
    public static final int MAX_COURSE_KEYWORD = 30;

    public static final String UNLINKED = "未关联批次";
    public static final String DISCLAIMER = "由 AI 生成，仅供参考";
    public static final String MSG_COPIED = "已复制回答";
    public static final String MSG_RATED = "已记录你的评价";
    public static final String MSG_NEW_CHAT = "已开始新对话";
    public static final String MSG_IMAGES_FULL = "最多附 9 张图片";
    public static final String MSG_BUSY = "上一个问题还在回答中，请稍后再问";
    public static final String MSG_CAMERA_DENIED = "需要相机权限才能拍照，也可以从相册选择";
    public static final String MSG_MIC_DENIED = "需要麦克风权限才能语音输入，可在系统设置中开启";
    public static final String MSG_MIC_GRANTED = "已开启麦克风，请按住说话";
    public static final String MSG_MIC_UNAVAILABLE = "麦克风暂时无法使用，请稍后再试";
    public static final String MSG_TOO_SHORT = "说话时间太短";
    public static final String MSG_NOT_HEARD = "没有听清，请再说一遍";
    public static final String MSG_ASR_UNAVAILABLE = "语音识别暂不可用，请稍后再试";
    public static final String MSG_TOO_FREQUENT = "操作太频繁，请稍后再试";
    public static final String MSG_NO_NETWORK = "网络未连接，你的问题已保留，连网后点重试";
    public static final String MSG_NETWORK_FAILED = "网络连接失败，请检查网络后重试";
    public static final String MSG_UPLOAD_FAILED = "图片上传失败，请重试或删除后再发送";
    public static final String MSG_STOPPED = "已停止生成";
    public static final String MSG_INTERRUPTED = "回答中断，点按重新生成";
    public static final String MSG_TIMEOUT = "回答超时，点按重新生成";
    public static final String MSG_UNAVAILABLE = "AI 助手暂时不可用，点按重新生成";
    public static final String MSG_NOT_IN_STOCK_RETRY = "这个批次已不在栏，请重新选择";
    public static final String MSG_PEN_FORBIDDEN = "你已不负责这个批次所在的栏舍，之后的提问不再带上该批次的数据";
    public static final String CONTEXT_DIVIDER = "以下为最近 10 轮，更早的问答不再参与回答";

    /**
     * 引用标记：服务端约定的 [^n]，以及服务端漏改时的模型原始写法 [^P3]、[^p3]、[^P1, P2]、[^1、2]、全角［＾P3］，
     * 都不能把原始标记显示给用户。
     */
    private static final Pattern CITATION = Pattern.compile(
            "[\\[［]\\s*[\\^＾]\\s*([Pp]?\\s*\\d+(?:\\s*[,，、;；]\\s*[Pp]?\\s*\\d+)*)\\s*[\\]］]");
    private static final Pattern CITATION_REF = Pattern.compile("([Pp]?)\\s*(\\d+)");
    /** 流式末尾未闭合的“[”“[^”“[^12”“[^P”“[^P1,” */
    private static final Pattern PARTIAL_TAIL = Pattern.compile("[\\[［]([\\^＾][\\sPp\\d,，、;；]*)?$");

    private AiText() {
    }

    // ===== 关联批次 =====

    /** 阶段名，空取品类名（“育肥猪”） */
    @Nullable
    public static String stageOf(@NonNull LinkedBatchVO b) {
        String s = b.getStageName();
        return s == null || s.isEmpty() ? b.getCategoryName() : s;
    }

    /** Chip：“ZM2610-031 · 育肥猪 · 96 日龄 · 存栏 200 头”；未关联为“未关联批次” */
    @NonNull
    public static String batchChip(@Nullable LinkedBatchVO b) {
        if (b == null) {
            return UNLINKED;
        }
        return b.getBatchNo() + " · " + nz(stageOf(b)) + " · " + b.getDayAge() + " 日龄 · 存栏 "
                + b.getCurrentCount() + " " + nz(b.getCountUnitText());
    }

    /** 面板主文字：“ZM2610-031 · 杜长大育肥猪” */
    @NonNull
    public static String batchTitle(@NonNull LinkedBatchVO b) {
        return b.getBatchNo() + " · " + nz(b.getBreedName()) + nz(stageOf(b));
    }

    /** 面板副文字：“3 号育肥舍 · 96 日龄 · 存栏 200 头” */
    @NonNull
    public static String batchSub(@NonNull LinkedBatchVO b) {
        return nz(b.getPenName()) + " · " + b.getDayAge() + " 日龄 · 存栏 " + b.getCurrentCount() + " "
                + nz(b.getCountUnitText());
    }

    @NonNull
    public static String batchChangedTip(@Nullable LinkedBatchVO b) {
        return b == null ? "已取消关联批次，之后的提问按通用养殖知识回答"
                : "已改为关联 " + b.getBatchNo() + "，之后的提问按这个批次回答";
    }

    /** 关联失效提示（NOT_IN_STOCK、NO_PERMISSION），其余为 null */
    @Nullable
    public static String invalidBatchTip(@Nullable String linkState, @Nullable String invalidBatchNo) {
        if (AiCodes.LINK_NOT_IN_STOCK.equals(linkState)) {
            return (invalidBatchNo == null ? "关联的批次" : invalidBatchNo) + " 已不在栏，之后的提问按通用养殖知识回答";
        }
        if (AiCodes.LINK_NO_PERMISSION.equals(linkState)) {
            return MSG_PEN_FORBIDDEN;
        }
        return null;
    }

    // ===== 输入栏 =====

    /** ≥480 字“485 / 500”；到 500 字“问题不能超过 500 字 · 500 / 500” */
    @Nullable
    public static String inputCount(int length) {
        if (length >= MAX_QUESTION) {
            return "问题不能超过 500 字 · " + MAX_QUESTION + " / " + MAX_QUESTION;
        }
        return length >= COUNT_FROM ? length + " / " + MAX_QUESTION : null;
    }

    /** “已附 2 张 · 最多 9 张”；有失败“1 张图片上传失败，点按重试” */
    @Nullable
    public static String attachHint(int count, int failed) {
        if (failed > 0) {
            return failed + " 张图片上传失败，点按重试";
        }
        return count > 0 ? "已附 " + count + " 张 · 最多 " + MAX_IMAGES + " 张" : null;
    }

    // ===== 回答正文 =====

    /** 去掉引用标记（[^n] 与 [^P3] 等原始写法） */
    @NonNull
    public static String stripCitations(@Nullable String text) {
        return text == null ? "" : CITATION.matcher(text).replaceAll("");
    }

    /** 流式显示：去掉完整的引用标记，末尾未闭合的“[”“[^”“[^12”“[^P3”暂缓到下一段 */
    @NonNull
    public static String streamingVisible(@NonNull String raw) {
        String s = CITATION.matcher(raw).replaceAll("");
        return PARTIAL_TAIL.matcher(s).replaceAll("");
    }

    /** 正文中的一个引用标记：在原文中的位置与对应的 seq_no（一个标记可含多个，也可能一个都没有） */
    public static final class CitationMark {
        public final int start;
        public final int end;
        @NonNull
        public final List<Integer> seqs;

        CitationMark(int start, int end, @NonNull List<Integer> seqs) {
            this.start = start;
            this.end = end;
            this.seqs = Collections.unmodifiableList(seqs);
        }
    }

    /**
     * 解析正文里的引用标记（按出现顺序）。[^n] 对应 seq_no n；[^Pn] 是服务端漏改的模型原始写法：正文里只有这种写法时
     * 按服务端同一规则（各 P 编号按首次出现顺序编为 1、2…）换成 seq_no；与 [^n] 混在一起时对应不上，只去掉、不显示角标。
     */
    @NonNull
    public static List<CitationMark> citationMarks(@Nullable String text) {
        List<CitationMark> marks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return marks;
        }
        List<int[]> spans = new ArrayList<>();
        List<List<int[]>> refs = new ArrayList<>();
        boolean hasPlain = false;
        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            List<int[]> list = new ArrayList<>();
            Matcher r = CITATION_REF.matcher(m.group(1));
            while (r.find()) {
                boolean p = !r.group(1).isEmpty();
                hasPlain |= !p;
                list.add(new int[]{p ? 1 : 0, parseIntOr(r.group(2))});
            }
            spans.add(new int[]{m.start(), m.end()});
            refs.add(list);
        }
        Map<Integer, Integer> seqByP = new LinkedHashMap<>();
        for (int i = 0; i < spans.size(); i++) {
            List<Integer> seqs = new ArrayList<>();
            for (int[] ref : refs.get(i)) {
                int seq;
                if (ref[0] == 0) {
                    seq = ref[1];
                } else if (hasPlain || ref[1] <= 0) {
                    continue;
                } else {
                    Integer mapped = seqByP.get(ref[1]);
                    if (mapped == null) {
                        mapped = seqByP.size() + 1;
                        seqByP.put(ref[1], mapped);
                    }
                    seq = mapped;
                }
                if (seq > 0 && !seqs.contains(seq)) {
                    seqs.add(seq);
                }
            }
            marks.add(new CitationMark(spans.get(i)[0], spans.get(i)[1], seqs));
        }
        return marks;
    }

    private static int parseIntOr(@NonNull String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 复制内容：去掉角标，末尾附固定声明 */
    @NonNull
    public static String copyText(@Nullable String answer) {
        return stripCitations(answer).trim() + "\n\n" + DISCLAIMER;
    }

    /** 未完成状态行文案 */
    @NonNull
    public static String statusLine(@Nullable String status, @Nullable String serverMessage, boolean hasText) {
        if (serverMessage != null && !serverMessage.isEmpty()) {
            return serverMessage;
        }
        if (AiCodes.STATUS_TIMEOUT.equals(status)) {
            return MSG_TIMEOUT;
        }
        if (AiCodes.STATUS_FAILED.equals(status) && !hasText) {
            return MSG_UNAVAILABLE;
        }
        return MSG_INTERRUPTED;
    }

    // ===== K02 =====

    /** “ZM2610-031 · 1 轮 · 09:12”“未关联批次 · 4 轮 · 10月13日”：今天和昨天写时刻，更早写日期 */
    @NonNull
    public static String sessionSub(@NonNull AiSessionItemVO s) {
        String batch = s.getBatchNo() == null || s.getBatchNo().isEmpty() ? UNLINKED : s.getBatchNo();
        return batch + " · " + s.getRoundCount() + " 轮 · " + shortTime(s.getLastAskedAt());
    }

    @NonNull
    static String shortTime(@Nullable String datetime) {
        LocalDateTime t = DateFormatter.parseDateTime(datetime);
        if (t == null) {
            return datetime == null ? "" : datetime;
        }
        LocalDate d = t.toLocalDate();
        LocalDate today = DateFormatter.today();
        if (d.equals(today) || d.equals(today.minusDays(1))) {
            return DateFormatter.formatTime(datetime);
        }
        return DateFormatter.monthDay(d);
    }

    // ===== 课堂 =====

    @NonNull
    public static String duration(int sec) {
        return DateFormatter.formatDuration(sec);
    }

    /** “看到 1:24 · 还剩 2:18” */
    @NonNull
    public static String continueText(@NonNull ContinueWatchingVO c) {
        return "看到 " + duration(c.getPositionSec()) + " · 还剩 " + duration(c.getRemainingSec());
    }

    /** “6 节 · 约 22 分钟”（总秒数 ÷ 60 四舍五入） */
    @NonNull
    public static String topicMeta(@NonNull TopicVO t) {
        return t.getCourseCount() + " 节 · 约 " + Math.round(t.getTotalDurationSec() / 60.0) + " 分钟";
    }

    @NonNull
    public static String learned(int learned, int total) {
        return "已学 " + learned + " / " + total;
    }

    /** 课程学习状态：“未观看”“已学完”“看到 2:43” */
    @NonNull
    public static String learnState(@Nullable String status, int positionSec) {
        if (AiCodes.LEARN_COMPLETED.equals(status)) {
            return "已学完";
        }
        if (AiCodes.LEARN_LEARNING.equals(status) && positionSec > 0) {
            return "看到 " + duration(positionSec);
        }
        return "未观看";
    }

    /** 视频卡副文字：“疾病防治 · 看到 2:43” */
    @NonNull
    public static String courseCardSub(@NonNull CourseCardVO c) {
        return nz(c.getCategoryName()) + " · " + learnState(c.getLearnStatus(), c.getPositionSec());
    }

    /** K05 本专题行：“2:58 · 已学完”“4:05 · 未观看” */
    @NonNull
    public static String topicCourseSub(@NonNull CourseCardVO c) {
        return duration(c.getDurationSec()) + " · " + learnState(c.getLearnStatus(), c.getPositionSec());
    }

    /** 本视频约 N MB（size_bytes ÷ 1048576 四舍五入） */
    public static long sizeMb(long bytes) {
        return Math.round(bytes / 1048576.0);
    }

    @NonNull
    public static String mobileTip(long bytes) {
        return "正在使用移动网络播放，本视频约 " + sizeMb(bytes) + " MB";
    }

    @NonNull
    public static String resumeTip(int sec) {
        return "从上次看到的 " + duration(sec) + " 继续播放";
    }

    /** 倍速显示：“1.0x”“1.25x” */
    @NonNull
    public static String speed(float s) {
        String v = (Math.abs(s * 10 - Math.round(s * 10)) < 0.001f) ? String.format(java.util.Locale.ROOT, "%.1f", s)
                : String.format(java.util.Locale.ROOT, "%.2f", s);
        return v + "x";
    }

    @NonNull
    public static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }
}
