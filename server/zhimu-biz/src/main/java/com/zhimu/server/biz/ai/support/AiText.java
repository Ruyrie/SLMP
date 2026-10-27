package com.zhimu.server.biz.ai.support;

import com.zhimu.server.common.util.BeijingTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 模块11 文案与派生值：会话标题、日期分组、主题、来源文字、学习状态、进度比例、续播位置。
 */
public final class AiText {

    /** 新建空会话的默认标题；首个提问后替换为问题摘要 */
    public static final String NEW_CHAT_TITLE = "新对话";

    private static final Pattern PUNCTUATION = Pattern.compile("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】…—·]+");
    private static final DateTimeFormatter MONTH_DAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final Pattern LEGACY_SOURCE_LEAD = Pattern.compile("^\\s*(?:\\*\\*)?"
            + Pattern.quote(AiCodes.NO_BASIS_LEAD) + "(?:\\*\\*)?\\s*");

    private AiText() {
    }

    /** 来源说明单独展示；同时兼容历史答案中曾经强制插入的首句。 */
    public static String answerBody(String answer) {
        return answer == null ? null : LEGACY_SOURCE_LEAD.matcher(answer).replaceFirst("");
    }

    /** 首问去标点与空白后截前 50 字；只有附图时为“图片提问” */
    public static String sessionTitle(String question) {
        if (question == null || question.isBlank()) {
            return "图片提问";
        }
        String text = PUNCTUATION.matcher(question.trim()).replaceAll("");
        if (text.isBlank()) {
            return "图片提问";
        }
        return text.length() <= 50 ? text : text.substring(0, 50);
    }

    /** 空会话标题：没有标题或仍是默认“新对话”时，首个提问可覆盖 */
    public static boolean isPlaceholderTitle(String title) {
        return title == null || title.isBlank() || NEW_CHAT_TITLE.equals(title.trim());
    }

    /** 会话列表日期分组：今天 TODAY、昨天 YESTERDAY、近 7 天 LAST_7_DAYS、更早 EARLIER */
    public static String dateGroup(LocalDateTime time) {
        if (time == null) {
            return "EARLIER";
        }
        LocalDate date = time.toLocalDate();
        LocalDate today = BeijingTime.today();
        if (date.equals(today)) {
            return "TODAY";
        }
        if (date.equals(today.minusDays(1))) {
            return "YESTERDAY";
        }
        if (date.isAfter(today.minusDays(7))) {
            return "LAST_7_DAYS";
        }
        return "EARLIER";
    }

    public static String dateGroupText(String group) {
        return switch (group) {
            case "TODAY" -> "今天";
            case "YESTERDAY" -> "昨天";
            case "LAST_7_DAYS" -> "近 7 天";
            default -> "更早";
        };
    }

    /** 主题：按关键词归类，取第一个命中的类别 */
    public static String topicOf(String question) {
        if (question == null || question.isBlank()) {
            return AiCodes.TOPIC_OTHER;
        }
        String text = question.toLowerCase(Locale.ROOT);
        if (containsAny(text, "药", "休药", "抗生素", "用量", "克伦特罗", "氟苯尼考")) {
            return AiCodes.TOPIC_MEDICATION;
        }
        if (containsAny(text, "料", "采食", "饲喂", "饲料", "喂")) {
            return AiCodes.TOPIC_FEEDING;
        }
        if (containsAny(text, "拉稀", "腹泻", "咳嗽", "发热", "高烧", "病", "死亡")) {
            return AiCodes.TOPIC_DISEASE;
        }
        if (containsAny(text, "疫苗", "免疫", "接种")) {
            return AiCodes.TOPIC_IMMUNE;
        }
        if (containsAny(text, "消毒", "熏蒸", "聚维酮碘")) {
            return AiCodes.TOPIC_DISINFECT;
        }
        return AiCodes.TOPIC_OTHER;
    }

    public static String sourceTypeText(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType) {
            case AiCodes.SOURCE_PLATFORM -> "平台知识库";
            case AiCodes.SOURCE_DISEASE -> "常见病症";
            case AiCodes.SOURCE_DRUG_CATALOG -> "兽药目录";
            default -> null;
        };
    }

    public static String learnStatus(boolean completed, int maxPositionSec) {
        if (completed) {
            return AiCodes.LEARN_COMPLETED;
        }
        return maxPositionSec > 0 ? AiCodes.LEARN_LEARNING : AiCodes.LEARN_NOT_STARTED;
    }

    /** 进度比例：已学完为 1；否则最远位置 ÷ 时长，保留 4 位小数 */
    public static java.math.BigDecimal progressRatio(boolean completed, int maxPositionSec, int durationSec) {
        if (completed) {
            return java.math.BigDecimal.ONE.setScale(4);
        }
        if (durationSec <= 0) {
            return java.math.BigDecimal.ZERO.setScale(4);
        }
        return java.math.BigDecimal.valueOf(Math.max(0, Math.min(maxPositionSec, durationSec)))
                .divide(java.math.BigDecimal.valueOf(durationSec), 4, java.math.RoundingMode.HALF_UP);
    }

    /** 续播位置：已学完、未观看、或距片尾不足 5 秒时为 0 */
    public static int resumePosition(boolean completed, int positionSec, int durationSec) {
        if (completed || positionSec <= 0 || durationSec - positionSec < 5) {
            return 0;
        }
        return positionSec;
    }

    public static String displayTime(LocalDateTime time) {
        return time == null ? null : time.format(MONTH_DAY_TIME);
    }

    /** 标题去掉标点后的紧凑文本，用于检索相关度 */
    public static String compact(String text) {
        return text == null ? "" : PUNCTUATION.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
