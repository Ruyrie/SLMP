package com.zhimu.slmp.ai;

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
 * 单元测试公共规则：io、computation 与主线程调度器换成同步执行（与时间有关的看门狗、50 ms 合并由测试传入 TestScheduler）；
 * “今天”固定为 2026-10-19；读取本组件 Mock（src/debug/assets/mock/ai）。
 */
public final class AiTestRules extends ExternalResource {

    public static final File MOCK_DIR = new File("src/debug/assets/mock/ai");

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

    public static String read(String name) throws IOException {
        return new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8)
                .replace("{{seq}}", "1");
    }

    public static JsonObject mockResponse(String name) throws IOException {
        return JsonParser.parseString(read(name)).getAsJsonObject();
    }

    public static <T> T mock(String name, Class<T> cls) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), cls);
    }

    public static <T> T mock(String name, Type type) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), type);
    }
}
