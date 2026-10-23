package com.zhimu.slmp.finance.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.ai.AiChatClient;
import com.zhimu.slmp.common.ai.AiChatRequest;
import com.zhimu.slmp.common.ai.AiChatResult;
import com.zhimu.slmp.common.ai.AiDirectConfig;
import com.zhimu.slmp.common.ai.AiJson;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.model.vo.MarketTrendVO;
import com.zhimu.slmp.finance.model.vo.SaleAdviceVO;
import com.zhimu.slmp.finance.model.vo.SaleBatchOptionVO;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * E12 AI 出栏建议测试直连（2026-10-22 用户确认：服务端完成前 debug 包直连千问，失败时 AiChatClient 自动改用 DeepSeek）。
 * 休药期内按规则生成、不调用模型（与服务端 RULE 模式一致）；其余调用模型取结论、时间段与风险，理由按模板生成。
 * 结果按批次在进程内缓存到当天（同服务端“当天已有 SUCCESS 直接返回”）；直连关闭（release 包没有 Key）时页面走 API-E33–E36。
 */
@FeatureScope
public class DirectAdviceRepository {

    private static final String TAG = "ZmtAi";
    static final int MAX_TOKENS = 800;
    static final double TEMPERATURE = 0.3;

    private final AiChatClient client;
    private final Gson gson;
    private final Map<Long, SaleAdviceVO> todayCache = new ConcurrentHashMap<>();
    /** 本地结果的 ID 为负数，避免与服务端建议混淆 */
    private final AtomicLong localId = new AtomicLong(-1);

    @Inject
    public DirectAdviceRepository(@NonNull Gson gson) {
        this(AiChatClient.getDefault(), gson);
    }

    DirectAdviceRepository(@NonNull AiChatClient client, @NonNull Gson gson) {
        this.client = client;
        this.gson = gson;
    }

    /** 直连开启（debug 包且配置了 Key） */
    public boolean isEnabled() {
        return AiDirectConfig.isEnabled() && client.isEnabled();
    }

    /** 当天已生成过的直连建议 */
    @Nullable
    public SaleAdviceVO cached(long batchId) {
        SaleAdviceVO v = todayCache.get(batchId);
        if (v == null || v.getGeneratedAt() == null
                || !v.getGeneratedAt().startsWith(DateFormatter.todayString())) {
            return null;
        }
        return v;
    }

    /** 生成一次建议（io 线程执行由调用方指定）；模型输出结构不合规时重试 1 次，仍失败返回 FAILED */
    @NonNull
    public Single<SaleAdviceVO> generate(@NonNull SaleBatchOptionVO batch, @Nullable MarketTrendVO trend) {
        LocalDateTime now = LocalDateTime.of(DateFormatter.today(), LocalTime.now(DateFormatter.ZONE).withNano(0));
        if (batch.isInWithdrawal()) {
            return Single.fromCallable(() -> store(batch, AdviceComposer.rule(batch, trend, now), "测试直连 · 规则生成"));
        }
        AiChatRequest request = AiChatRequest.builder()
                .tag("sale_advice")
                .system(AdviceComposer.system())
                .user(AdviceComposer.user(batch, trend, now.toLocalDate()))
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE)
                .build();
        Single<AiChatResult> call = client.chat(request);
        return call.flatMap(r -> {
                    JsonObject vo = AdviceComposer.fromModel(AiJson.extractObject(r.getContent()), batch, trend, now);
                    return vo != null ? Single.just(store(batch, vo, r.sourceLabel()))
                            : client.chat(request).map(r2 -> {
                        JsonObject vo2 = AdviceComposer.fromModel(AiJson.extractObject(r2.getContent()), batch,
                                trend, now);
                        if (vo2 == null) {
                            throw new IllegalStateException("AI 回答格式不符合要求");
                        }
                        return store(batch, vo2, r2.sourceLabel());
                    });
                })
                .timeout(AiChatClient.CALL_TIMEOUT_SEC * 2 + 30, TimeUnit.SECONDS)
                .onErrorReturn(e -> {
                    Log.w(TAG, "直连出栏建议失败：" + e.getMessage());
                    return failed(batch, now);
                });
    }

    @NonNull
    private SaleAdviceVO store(@NonNull SaleBatchOptionVO batch, @NonNull JsonObject json, @NonNull String source) {
        json.addProperty("id", localId.getAndDecrement());
        json.addProperty("source_label", source);
        SaleAdviceVO vo = gson.fromJson(json, SaleAdviceVO.class);
        todayCache.put(batch.getBatchId(), vo);
        Log.i(TAG, "直连出栏建议完成 batch=" + batch.getBatchNo() + " " + source);
        return vo;
    }

    @NonNull
    private SaleAdviceVO failed(@NonNull SaleBatchOptionVO batch, @NonNull LocalDateTime now) {
        JsonObject o = new JsonObject();
        o.addProperty("id", localId.getAndDecrement());
        o.addProperty("batch_id", batch.getBatchId());
        o.addProperty("batch_no", batch.getBatchNo());
        o.addProperty("pen_name", batch.getPenName());
        o.addProperty("status", FinanceConst.GEN_FAILED);
        o.addProperty("in_withdrawal", batch.isInWithdrawal());
        o.addProperty("generated_at", now.format(java.time.format.DateTimeFormatter.ofPattern(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)));
        return gson.fromJson(o, SaleAdviceVO.class);
    }
}
