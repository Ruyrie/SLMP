package com.zhimu.slmp.ai.model;

/**
 * 模块11 枚举取值（技术文档 2.3.2）。服务端按 String 返回，这里集中定义常量。
 */
public final class AiCodes {

    private AiCodes() {
    }

    // AiMessageStatus
    public static final String STATUS_ANSWERING = "ANSWERING";
    public static final String STATUS_ANSWERED = "ANSWERED";
    public static final String STATUS_INTERRUPTED = "INTERRUPTED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_FAILED = "FAILED";

    // AiAnswerType
    public static final String ANSWER_NORMAL = "NORMAL";
    public static final String ANSWER_NO_BASIS = "NO_BASIS";
    public static final String ANSWER_OFF_TOPIC = "OFF_TOPIC";
    public static final String ANSWER_BANNED_DRUG = "BANNED_DRUG";
    public static final String ANSWER_EPIDEMIC = "EPIDEMIC";

    // AiRating
    public static final String RATING_USEFUL = "USEFUL";
    public static final String RATING_USELESS = "USELESS";

    // BatchLinkState
    public static final String LINK_NONE = "NONE";
    public static final String LINK_VALID = "VALID";
    public static final String LINK_NOT_IN_STOCK = "NOT_IN_STOCK";
    public static final String LINK_NO_PERMISSION = "NO_PERMISSION";

    // AiTopic
    public static final String TOPIC_MEDICATION = "MEDICATION";
    public static final String TOPIC_FEEDING = "FEEDING";
    public static final String TOPIC_DISEASE = "DISEASE";
    public static final String TOPIC_IMMUNE = "IMMUNE";
    public static final String TOPIC_DISINFECT = "DISINFECT";

    // CitationIcon / KnowledgeSource
    public static final String ICON_ARTICLE = "ARTICLE";
    public static final String SOURCE_PLATFORM = "PLATFORM";
    public static final String SOURCE_DISEASE = "DISEASE";
    public static final String SOURCE_DRUG_CATALOG = "DRUG_CATALOG";

    // LearnStatus
    public static final String LEARN_NOT_STARTED = "NOT_STARTED";
    public static final String LEARN_LEARNING = "LEARNING";
    public static final String LEARN_COMPLETED = "COMPLETED";

    // VideoQuality / NetworkType
    public static final String QUALITY_720 = "P720";
    public static final String QUALITY_480 = "P480";
    public static final String NET_WIFI = "WIFI";
    public static final String NET_MOBILE = "MOBILE";
    public static final String NET_UNKNOWN = "UNKNOWN";

    // PlayEntry
    public static final String ENTRY_COURSE = "COURSE";
    public static final String ENTRY_TOPIC = "TOPIC";
    public static final String ENTRY_CONTINUE = "CONTINUE";
    public static final String ENTRY_HISTORY = "HISTORY";
    public static final String ENTRY_KNOWLEDGE = "KNOWLEDGE";

    // ProgressEvent
    public static final String EVENT_START = "START";
    public static final String EVENT_TICK = "TICK";
    public static final String EVENT_PAUSE = "PAUSE";
    public static final String EVENT_SEEK = "SEEK";
    public static final String EVENT_SWITCH = "SWITCH";
    public static final String EVENT_LEAVE = "LEAVE";
    public static final String EVENT_END = "END";

    // SSE 事件名
    public static final String SSE_META = "meta";
    public static final String SSE_DELTA = "delta";
    public static final String SSE_CITATION = "citation";
    public static final String SSE_DONE = "done";
    public static final String SSE_ERROR = "error";
}
