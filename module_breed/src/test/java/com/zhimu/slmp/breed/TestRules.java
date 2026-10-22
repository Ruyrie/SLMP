package com.zhimu.slmp.breed;

import com.google.gson.Gson;
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
 * 单元测试公共规则：RxJava 调度器同步执行；“今天”固定为 2026-10-19（Mock 数据的今天）；读取本组件 Mock 响应的 data。
 */
public final class TestRules extends ExternalResource {

    private static final Gson GSON = GsonFactory.get();
    private static final File MOCK_DIR = new File("src/debug/assets/mock/breed");

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

    /** Mock 响应文件（完整 ApiResponse） */
    public static JsonObject mock(String name) throws IOException {
        String text = new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8);
        return JsonParser.parseString(text.replace("{{seq}}", "9101")).getAsJsonObject();
    }

    /** Mock 响应文件的 data 解析为 VO */
    public static <T> T data(String name, Type type) throws IOException {
        return GSON.fromJson(mock(name).get("data"), type);
    }

    public static File mockDir() {
        return MOCK_DIR;
    }
}
