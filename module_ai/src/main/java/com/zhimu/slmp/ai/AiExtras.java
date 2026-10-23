package com.zhimu.slmp.ai;

/**
 * 模块11 页面参数键（RouterPath 中还没有的先定义在本组件，交付时列出，由主会话合并进 RouterPath）。
 * 已有的沿用 RouterPath：EXTRA_QUESTION（T01 推荐问题）、EXTRA_BATCH_ID、EXTRA_PEN_ID（进 I05）。
 */
public final class AiExtras {

    private AiExtras() {
    }


    public static final String MODE_CHAT = "chat";
    public static final String MODE_COURSE = "course";
}
