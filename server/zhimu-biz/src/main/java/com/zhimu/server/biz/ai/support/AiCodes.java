package com.zhimu.server.biz.ai.support;

/**
 * 模块11 枚举取值（与 App 的 AiCodes 一致）。服务端按 String 返回。
 */
public final class AiCodes {

    private AiCodes() {
    }

    // AI_MESSAGE_STATUS
    public static final String STATUS_ANSWERING = "ANSWERING";
    public static final String STATUS_ANSWERED = "ANSWERED";
    public static final String STATUS_INTERRUPTED = "INTERRUPTED";
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    public static final String STATUS_FAILED = "FAILED";
    /** 逻辑删除：t_ai_message 没有 deleted 列，会话删除后把其消息 status 置为该值 */
    public static final String STATUS_DELETED = "DELETED";

    // AI_ANSWER_TYPE
    public static final String ANSWER_NORMAL = "NORMAL";
    public static final String ANSWER_NO_BASIS = "NO_BASIS";
    public static final String ANSWER_OFF_TOPIC = "OFF_TOPIC";
    public static final String ANSWER_BANNED_DRUG = "BANNED_DRUG";
    public static final String ANSWER_EPIDEMIC = "EPIDEMIC";

    // AI_RATING
    public static final String RATING_USEFUL = "USEFUL";
    public static final String RATING_USELESS = "USELESS";

    // BATCH_LINK_STATE
    public static final String LINK_NONE = "NONE";
    public static final String LINK_VALID = "VALID";
    public static final String LINK_NOT_IN_STOCK = "NOT_IN_STOCK";
    public static final String LINK_NO_PERMISSION = "NO_PERMISSION";
    /**
     * 逻辑删除：t_ai_session 没有 deleted 列，会话删除后把 batch_link_state 置为该值。
     * 列表、详情、提问、重新生成均按 40401 处理。
     */
    public static final String LINK_DELETED = "DELETED";

    // AI_TOPIC
    public static final String TOPIC_MEDICATION = "MEDICATION";
    public static final String TOPIC_FEEDING = "FEEDING";
    public static final String TOPIC_DISEASE = "DISEASE";
    public static final String TOPIC_IMMUNE = "IMMUNE";
    public static final String TOPIC_DISINFECT = "DISINFECT";
    public static final String TOPIC_OTHER = "OTHER";

    // KNOWLEDGE_SOURCE / CITATION_ICON
    public static final String SOURCE_PLATFORM = "PLATFORM";
    public static final String SOURCE_DISEASE = "DISEASE";
    public static final String SOURCE_DRUG_CATALOG = "DRUG_CATALOG";
    public static final String ICON_STANDARD = "STANDARD";
    public static final String ICON_ARTICLE = "ARTICLE";

    // LEARN_STATUS
    public static final String LEARN_NOT_STARTED = "NOT_STARTED";
    public static final String LEARN_LEARNING = "LEARNING";
    public static final String LEARN_COMPLETED = "COMPLETED";

    // VIDEO_QUALITY / NETWORK_TYPE / PLAY_ENTRY / PROGRESS_EVENT
    public static final String QUALITY_720 = "P720";
    public static final String QUALITY_480 = "P480";
    public static final String NET_WIFI = "WIFI";
    public static final String NET_MOBILE = "MOBILE";
    public static final String NET_UNKNOWN = "UNKNOWN";

    public static final String ENTRY_COURSE = "COURSE";
    public static final String ENTRY_TOPIC = "TOPIC";
    public static final String ENTRY_CONTINUE = "CONTINUE";
    public static final String ENTRY_HISTORY = "HISTORY";
    public static final String ENTRY_KNOWLEDGE = "KNOWLEDGE";

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

    // FINISH_REASON
    public static final String FINISH_STOP = "STOP";
    public static final String FINISH_FIXED = "FIXED";
    public static final String FINISH_REPLACED = "REPLACED";

    public static final String TOPIC_ACTION_START = "START";
    public static final String TOPIC_ACTION_CONTINUE = "CONTINUE";
    public static final String TOPIC_ACTION_REVIEW = "REVIEW";

    public static final String NO_BASIS_LEAD = "未找到平台知识依据，仅供参考。";
    public static final String OFF_TOPIC_ANSWER = "这个问题与畜禽养殖无关，我不能回答。";
}
