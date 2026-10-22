package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;

/**
 * 一个 OpenAI 兼容的 Chat Completions 服务商（测试直连用）：名称、base_url、模型与 Key。
 * Key 只在构造请求头时使用，{@link #toString()} 不包含 Key。
 */
public final class AiProvider {

    /** 阿里云百炼（通义千问） */
    public static final String DASHSCOPE = "dashscope";
    /** DeepSeek */
    public static final String DEEPSEEK = "deepseek";

    public static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    @NonNull
    private final String name;
    @NonNull
    private final String displayName;
    @NonNull
    private final String baseUrl;
    @NonNull
    private final String model;
    @NonNull
    private final String apiKey;

    public AiProvider(@NonNull String name, @NonNull String displayName, @NonNull String baseUrl,
                      @NonNull String model, @NonNull String apiKey) {
        this.name = name;
        this.displayName = displayName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
    }

    /** Key 为空视为未开启 */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.trim().isEmpty() && model != null && !model.trim().isEmpty();
    }

    @NonNull
    public String getName() {
        return name;
    }

    /** 界面显示的名称，例如“通义千问” */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getBaseUrl() {
        return baseUrl;
    }

    @NonNull
    public String getModel() {
        return model;
    }

    /** 只在 AiChatClient 内部使用，不要写入日志 */
    @NonNull
    String apiKey() {
        return apiKey;
    }

    @NonNull
    public String chatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    @NonNull
    @Override
    public String toString() {
        return name + "(" + model + ")";
    }
}
