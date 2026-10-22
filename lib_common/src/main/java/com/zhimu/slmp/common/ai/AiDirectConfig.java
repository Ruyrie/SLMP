package com.zhimu.slmp.common.ai;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 测试直连的配置（《App开发约定》一节“测试直连”，2026-10-22 用户确认）：服务端完成前，debug 包从
 * lib_common 的 BuildConfig 读取 Key（local.properties 的 ai.dashscopeKey、ai.deepseekKey），
 * 主用阿里云百炼、失败时用 DeepSeek；release 包 Key 为空字符串，直连自动关闭，业务改走服务端接口。
 * 正式上线前删除直连，改回服务端调用。
 */
public final class AiDirectConfig {

    private AiDirectConfig() {
    }

    /** 主用：阿里云百炼 OpenAI 兼容接口，模型 BuildConfig.DASHSCOPE_MODEL */
    @NonNull
    public static AiProvider dashscope() {
        return new AiProvider(AiProvider.DASHSCOPE, "通义千问", AiProvider.DASHSCOPE_BASE_URL,
                BuildConfig.DASHSCOPE_MODEL, BuildConfig.DASHSCOPE_API_KEY);
    }

    /** 备用：DeepSeek，模型 BuildConfig.DEEPSEEK_MODEL */
    @NonNull
    public static AiProvider deepseek() {
        return new AiProvider(AiProvider.DEEPSEEK, "DeepSeek", AiProvider.DEEPSEEK_BASE_URL,
                BuildConfig.DEEPSEEK_MODEL, BuildConfig.DEEPSEEK_API_KEY);
    }

    /** 按优先级排列、Key 不为空的服务商；为空表示直连关闭 */
    @NonNull
    public static List<AiProvider> enabledProviders() {
        List<AiProvider> list = new ArrayList<>(2);
        AiProvider primary = dashscope();
        if (primary.isEnabled()) {
            list.add(primary);
        }
        AiProvider backup = deepseek();
        if (backup.isEnabled()) {
            list.add(backup);
        }
        return Collections.unmodifiableList(list);
    }

    /** 任一服务商有 Key 即开启（只在 debug 包中可能为真） */
    public static boolean isEnabled() {
        return !enabledProviders().isEmpty();
    }
}
