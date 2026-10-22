package com.zhimu.slmp.feed;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 单元测试公共规则：RxJava 的 io、computation 与主线程调度器换成同步执行；“今天”固定为 2026-10-19（Mock 数据的今天）。
 * 另提供读取本组件 Mock 响应（src/debug/assets/mock/feed）的工具，测试数据与 Mock 一致。
 */
public final class TestRules extends ExternalResource {

    public static final File MOCK_DIR = new File("src/debug/assets/mock/feed");

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

    /** 不换行空格（NumberFormatter.NBSP）换成普通空格、减号换成连字符，便于和文档文案比较 */
    public static String plain(String text) {
        return text == null ? null : text.replace(' ', ' ').replace('−', '-');
    }

    /** 读取 Mock 响应文件（{{seq}} 替换为 1） */
    public static JsonObject mockResponse(String name) throws IOException {
        String text = new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8)
                .replace("{{seq}}", "1");
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** Mock 响应的 data 解析为 VO */
    public static <T> T mock(String name, Class<T> cls) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), cls);
    }

    public static <T> T mock(String name, Type type) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), type);
    }
}
