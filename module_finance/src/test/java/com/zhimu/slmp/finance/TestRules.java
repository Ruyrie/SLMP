package com.zhimu.slmp.finance;

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
import io.reactivex.rxjava3.schedulers.TestScheduler;

/**
 * 单元测试公共规则：io、主线程调度器同步执行；computation 换成 {@link #time}（防抖、轮询用 advanceTimeBy 推进）；
 * “今天”固定为 2026-10-19（Mock 数据的今天）。另提供读取本组件 Mock 响应（src/debug/assets/mock/finance）的工具。
 */
public final class TestRules extends ExternalResource {

    public static final File MOCK_DIR = new File("src/debug/assets/mock/finance");

    public final TestScheduler time = new TestScheduler();

    @Override
    protected void before() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(s -> time);
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

    /** 不换行空格换成普通空格、减号换成连字符，便于和文档文案比较 */
    public static String plain(CharSequence text) {
        return text == null ? null : text.toString().replace(' ', ' ').replace('−', '-');
    }

    public static JsonObject mockResponse(String name) throws IOException {
        String text = new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8)
                .replace("\"{{seq}}\"", "1").replace("{{seq}}", "1");
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static <T> T mock(String name, Class<T> cls) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), cls);
    }

    public static <T> T mock(String name, Type type) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), type);
    }

    public static com.zhimu.slmp.finance.model.vo.SubcategoryVO mockSub(long id, String name) {
        return GsonFactory.get().fromJson("{\"id\":" + id + ",\"name\":\"" + name + "\"}",
                com.zhimu.slmp.finance.model.vo.SubcategoryVO.class);
    }
}
