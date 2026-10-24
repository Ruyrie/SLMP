package com.zhimu.server.admin.content.support;

import java.util.Map;

/** 内容运营枚举中文名。 */
public final class ContentTexts {

    private static final Map<String, String> CONTENT_STATUS = Map.of(
            "DRAFT", "草稿", "ON_SHELF", "已上架", "OFF_SHELF", "已下架");

    private static final Map<String, String> TRANSCODE_STATUS = Map.of(
            "NONE", "未绑定视频", "WAITING", "等待转码", "PROCESSING", "转码中",
            "SUCCESS", "转码完成", "FAILED", "转码失败");

    private static final Map<String, String> SOURCE_TYPE = Map.of(
            "PLATFORM", "平台知识库", "DISEASE", "常见病症", "DRUG_CATALOG", "兽药目录");

    private static final Map<String, String> TASK_STATUS = Map.of(
            "PENDING", "待处理", "DONE", "已完成", "CLOSED", "已关闭");

    private static final Map<String, String> TASK_SOURCE = Map.of(
            "AI_QA", "AI 问答", "CORRECTION", "更正说明", "MANUAL", "手动创建");

    private static final Map<String, String> TASK_MODE = Map.of(
            "NEW_ENTRY", "新建条目", "SUPPLEMENT", "补充已有条目");

    private ContentTexts() {
    }

    public static String contentStatus(String code) {
        return code == null ? null : CONTENT_STATUS.getOrDefault(code, code);
    }

    public static String transcodeStatus(String code) {
        return code == null ? null : TRANSCODE_STATUS.getOrDefault(code, code);
    }

    public static String sourceType(String code) {
        return code == null ? null : SOURCE_TYPE.getOrDefault(code, code);
    }

    public static String taskStatus(String code) {
        return code == null ? null : TASK_STATUS.getOrDefault(code, code);
    }

    public static String taskSource(String code) {
        return code == null ? null : TASK_SOURCE.getOrDefault(code, code);
    }

    public static String taskMode(String code) {
        return code == null ? null : TASK_MODE.getOrDefault(code, code);
    }

    /** 专题总时长文案：总秒数 ÷ 60 四舍五入，“约 22 分钟” */
    public static String durationText(int totalSeconds) {
        int minutes = (int) Math.round(totalSeconds / 60.0);
        return "约 " + minutes + " 分钟";
    }
}
