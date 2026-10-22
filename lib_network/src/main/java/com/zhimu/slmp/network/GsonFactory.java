package com.zhimu.slmp.network;

import androidx.annotation.NonNull;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

/**
 * 全应用共用的 Gson：下划线命名策略（created_at ↔ createdAt）；未声明类型的数字按 BigDecimal 解析（金额、重量不丢精度）。
 * 由 NetworkModule 以单例提供，Retrofit、草稿、字典缓存共用。
 */
public final class GsonFactory {

    private static volatile Gson instance;

    private GsonFactory() {
    }

    @NonNull
    public static Gson get() {
        if (instance == null) {
            synchronized (GsonFactory.class) {
                if (instance == null) {
                    instance = new GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                            .setObjectToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
                            .setNumberToNumberStrategy(ToNumberPolicy.BIG_DECIMAL)
                            .disableHtmlEscaping()
                            .create();
                }
            }
        }
        return instance;
    }
}
