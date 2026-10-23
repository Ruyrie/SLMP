package com.zhimu.slmp.ai;

/**
 * 模块11 页面参数键（RouterPath 中还没有的先定义在本组件，交付时列出，由主会话合并进 RouterPath）。
 * 已有的沿用 RouterPath：EXTRA_QUESTION（T01 推荐问题）、EXTRA_BATCH_ID、EXTRA_PEN_ID（进 I05）。
 */
public final class AiExtras {

    private AiExtras() {
    }

    /** 知识条目 ID（long，打开 K03） */
    public static final String EXTRA_KNOWLEDGE_ID = "knowledge_id";
    /** 引用 ID（long，K01 角标进入 K03 时带，相关条目不带） */
    public static final String EXTRA_CITATION_ID = "citation_id";
    /** 课程 ID（long，打开 K05；K05“问 AI”进 K01 时作为课程背景） */
    public static final String EXTRA_COURSE_ID = "course_id";
    /** 课程标题（String，K05“问 AI”进 K01 时显示“正在问：……”） */
    public static final String EXTRA_COURSE_TITLE = "course_title";
    /** 专题 ID（long，打开 K07） */
    public static final String EXTRA_TOPIC_ID = "topic_id";
    /** 播放入口（String，PlayEntry：COURSE、TOPIC、CONTINUE、HISTORY、KNOWLEDGE） */
    public static final String EXTRA_ENTRY_TYPE = "entry_type";
    /** 会话 ID（long，K02 返回 K01） */
    public static final String EXTRA_SESSION_ID = "session_id";
    /** 新会话（boolean，K02“新对话”返回、外部进入 K01） */
    public static final String EXTRA_NEW_SESSION = "new_session";
    /** 助手页模式（String：chat / course，K01、K04 两个 action 进入助手页） */
    public static final String EXTRA_ASSISTANT_MODE = "assistant_mode";
    /** 直接进入横屏全屏（boolean，K06 或 K03 内嵌视频点全屏） */
    public static final String EXTRA_FULLSCREEN = "fullscreen";
    /** 起播位置（long 毫秒，K03 内嵌视频进入全屏时接着播） */
    public static final String EXTRA_START_MS = "start_ms";

    public static final String MODE_CHAT = "chat";
    public static final String MODE_COURSE = "course";
}
