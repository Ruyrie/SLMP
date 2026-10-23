package com.zhimu.slmp.stock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.stock.data.StockDraftRepository;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;
import com.zhimu.slmp.stock.util.SubmitGuard;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 单元测试公共规则：RxJava 调度器换成同步执行，防抖与 API-M42 轮询间隔改为 0；“今天”固定为 2026-10-19（Mock 数据的今天）。
 * 另提供读取本组件 Mock 响应（src/debug/assets/mock/stock）的工具，测试数据与 Mock 一致。
 */
public final class TestRules extends ExternalResource {

    public static final File MOCK_DIR = new File("src/debug/assets/mock/stock");

    @Override
    protected void before() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setNewThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        DateFormatter.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), DateFormatter.ZONE));
        SubmitGuard.pollIntervalMs = 0;
        StockFormViewModel.debounceMs = 0;
    }

    @Override
    protected void after() {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        DateFormatter.setClock(Clock.system(DateFormatter.ZONE));
        SubmitGuard.pollIntervalMs = 2000;
        StockFormViewModel.debounceMs = 300;
    }

    /** 不换行空格换成普通空格、减号换成连字符，便于和文档文案比较 */
    public static String plain(String text) {
        return text == null ? null : text.replace(' ', ' ').replace('−', '-');
    }

    public static JsonObject mockResponse(String name) throws IOException {
        String text = new String(Files.readAllBytes(new File(MOCK_DIR, name).toPath()), StandardCharsets.UTF_8)
                .replace("{{seq}}", "1");
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static <T> T mock(String name, Class<T> cls) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), cls);
    }

    public static <T> T mock(String name, Type type) throws IOException {
        return GsonFactory.get().fromJson(mockResponse(name).get("data"), type);
    }

    /** 没有草稿的草稿仓库 */
    public static StockDraftRepository noDrafts() {
        StockDraftRepository d = org.mockito.Mockito.mock(StockDraftRepository.class);
        when(d.getDraft(anyString(), anyString())).thenReturn(Maybe.empty());
        when(d.saveDraft(anyString(), anyString(), anyString())).thenReturn(Completable.complete());
        when(d.deleteDraft(anyString(), anyString())).thenReturn(Completable.complete());
        return d;
    }
}
