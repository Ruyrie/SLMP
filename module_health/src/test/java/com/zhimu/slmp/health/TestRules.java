package com.zhimu.slmp.health;

import com.google.gson.Gson;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.rules.ExternalResource;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Instant;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 单元测试公共规则：RxJava 调度器同步执行；“今天”固定为 2026-10-19 10:00（Mock 数据的今天）。
 */
public final class TestRules extends ExternalResource {

    private static final Gson GSON = GsonFactory.get();

    @Override
    protected void before() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setNewThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        DateFormatter.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), DateFormatter.ZONE));
    }

    @Override
    protected void after() {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        DateFormatter.setClock(Clock.system(DateFormatter.ZONE));
    }

    /** 数量与单位之间的不换行空格换成普通空格，便于和文档文案比较 */
    public static String plain(String text) {
        return text == null ? null : text.replace(' ', ' ');
    }

    /** 用应用的 Gson（下划线命名）把 JSON 解析为 VO，单引号写法方便在 Java 字符串中书写 */
    public static <T> T parse(String json, Class<T> cls) {
        return GSON.fromJson(json.replace('\'', '"'), cls);
    }

    public static <T> T parse(String json, Type type) {
        return GSON.fromJson(json.replace('\'', '"'), type);
    }
}
