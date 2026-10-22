package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 组件服务注册表：业务组件在 IModuleEntry.onCreate 中注册 lib_common 定义的服务接口实现，调用方只依赖接口。
 * 组件不存在（例如单独运行时）时 get 返回 null，调用方需要降级处理。
 */
public final class ServiceHub {

    private static final ConcurrentHashMap<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private ServiceHub() {
    }

    public static <T> void register(@NonNull Class<T> type, @NonNull T impl) {
        SERVICES.put(type, impl);
    }

    @Nullable
    public static <T> T get(@NonNull Class<T> type) {
        Object o = SERVICES.get(type);
        return o == null ? null : type.cast(o);
    }

    /** 必须存在的服务；不存在时抛出 IllegalStateException */
    @NonNull
    public static <T> T require(@NonNull Class<T> type) {
        T t = get(type);
        if (t == null) {
            throw new IllegalStateException("服务未注册：" + type.getName());
        }
        return t;
    }

    public static boolean has(@NonNull Class<?> type) {
        return SERVICES.containsKey(type);
    }
}
