package com.zhimu.slmp.ai.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.zhimu.slmp.ai.model.AiStreamEvent;
import com.zhimu.slmp.ai.model.request.AiAskRequest;
import com.zhimu.slmp.ai.model.vo.AiMessageSliceVO;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.AiSessionDetailVO;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.model.vo.KnowledgeDetailVO;
import com.zhimu.slmp.ai.model.vo.LinkableBatchesVO;
import com.zhimu.slmp.ai.model.vo.RatingResultVO;
import com.zhimu.slmp.ai.model.vo.SpeechResultVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.common.ai.AiSseEvent;
import com.zhimu.slmp.common.ai.AiSseReader;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.Headers;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.paging.PagedSource;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import retrofit2.Call;
import retrofit2.Response;

/**
 * AI 问答数据（API-K01–K10、K12）。流式接口按技术文档 2.6.1：
 * 响应为 application/json 时解析 ApiResponse 抛 ApiException（开流前错误）；为 text/event-stream 时用 {@link AiSseReader}
 * 逐行读取，每个事件转为 {@link AiStreamEvent}；Flowable 用 BUFFER 背压，取消订阅即 Call.cancel。
 * Mock（MockInterceptor 一次返回整段 SSE，Content-Type 为 JSON）时按事件间隔回放，模拟逐段到达。
 */
@FeatureScope
public class AiRepository {

    /** Mock 回放时相邻 delta 的间隔 */
    static final long MOCK_DELTA_INTERVAL_MS = 90;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AiApi api;
    private final AiStreamApi streamApi;
    private final Gson gson;

    @Inject
    public AiRepository(@NonNull AiApi api, @NonNull AiStreamApi streamApi, @NonNull Gson gson) {
        this.api = api;
        this.streamApi = streamApi;
        this.gson = gson;
    }

    /** API-K01 历史会话分页（keyword 为空时不传） */
    @NonNull
    public PagedSource<AiSessionItemVO> sessionSource(@Nullable String keyword) {
        String k = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return new PagedSource<>((page, size) -> api.sessions(k, page, size).compose(RxResult.unwrap()));
    }

    @NonNull
    public Single<AiSessionDetailVO> getSession(long sessionId) {
        return api.session(sessionId).compose(RxResult.unwrap());
    }

    /** API-K03：beforeRoundNo 为空时取最近 size 轮 */
    @NonNull
    public Single<AiMessageSliceVO> getMessages(long sessionId, @Nullable Integer beforeRoundNo, int size) {
        return api.messages(sessionId, beforeRoundNo, null, size).compose(RxResult.unwrap());
    }

    /** 重命名会话（PATCH title，1–40 字） */
    @NonNull
    public Single<AiSessionDetailVO> renameSession(long sessionId, @NonNull String title) {
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        return api.updateSession(sessionId, RequestBody.create(body.toString(), JSON)).compose(RxResult.unwrap());
    }

    /** 删除会话 */
    @NonNull
    public Completable deleteSession(long sessionId) {
        return RxResult.completable(api.deleteSession(sessionId));
    }

    /** API-K04：batchId 为 null 表示取消关联（序列化保留 null） */
    @NonNull
    public Single<AiSessionDetailVO> changeBatch(long sessionId, @Nullable Long batchId) {
        JsonObject body = new JsonObject();
        body.addProperty("batch_id", batchId);
        return api.changeBatch(sessionId, RequestBody.create(body.toString(), JSON)).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<LinkableBatchesVO> getLinkableBatches() {
        return api.linkableBatches().compose(RxResult.unwrap());
    }

    /** API-K06 提问（流式） */
    @NonNull
    public Flowable<AiStreamEvent> ask(@NonNull AiAskRequest request, @NonNull String idemKey) {
        return openStream(() -> streamApi.ask(idemKey, request));
    }

    /** API-K07 重新生成（流式） */
    @NonNull
    public Flowable<AiStreamEvent> retry(long messageId) {
        return openStream(() -> streamApi.retry(messageId));
    }

    @NonNull
    public Single<AiMessageVO> getMessage(long messageId) {
        return api.message(messageId).compose(RxResult.unwrap());
    }

    /** API-K09：rating 为 null 取消评价（序列化保留 null） */
    @NonNull
    public Single<RatingResultVO> rate(long messageId, @Nullable String rating) {
        JsonObject body = new JsonObject();
        body.addProperty("rating", rating);
        return api.rate(messageId, RequestBody.create(body.toString(), JSON)).compose(RxResult.unwrap());
    }

    /** API-K10 语音转文字（16kHz WAV，multipart） */
    @NonNull
    public Single<SpeechResultVO> recognize(@NonNull File wavFile, int durationMs) {
        MultipartBody.Part audio = MultipartBody.Part.createFormData("audio", wavFile.getName(),
                RequestBody.create(wavFile, MediaType.get("audio/wav")));
        RequestBody duration = RequestBody.create(String.valueOf(durationMs), MediaType.get("text/plain"));
        return api.recognize(audio, duration).compose(RxResult.unwrap());
    }

    @NonNull
    public Single<KnowledgeDetailVO> getKnowledge(long knowledgeId, @Nullable Long citationId) {
        return api.knowledge(knowledgeId, citationId).compose(RxResult.unwrap());
    }

    // ===== SSE =====

    @NonNull
    private Flowable<AiStreamEvent> openStream(@NonNull Supplier<Call<ResponseBody>> factory) {
        return Flowable.create(emitter -> read(factory.get(), emitter), BackpressureStrategy.BUFFER);
    }

    private void read(@NonNull Call<ResponseBody> call, @NonNull FlowableEmitter<AiStreamEvent> emitter) {
        emitter.setCancellable(call::cancel);
        Response<ResponseBody> resp;
        try {
            resp = call.execute();
        } catch (IOException e) {
            if (!emitter.isCancelled()) {
                emitter.tryOnError(ApiException.network(e));
            }
            return;
        }
        if (!resp.isSuccessful()) {
            String body = null;
            try (ResponseBody eb = resp.errorBody()) {
                body = eb == null ? null : eb.string();
            } catch (IOException ignored) {
                // 按无错误体处理
            }
            emitter.tryOnError(RxResult.parseErrorBody(resp.code(), body, resp.headers().get(Headers.TRACE_ID), null));
            return;
        }
        ResponseBody rb = resp.body();
        if (rb == null) {
            emitter.tryOnError(ApiException.unknown(null));
            return;
        }
        try (ResponseBody closing = rb) {
            BufferedSource source = closing.source();
            MediaType type = closing.contentType();
            boolean eventStream = type != null && "event-stream".equalsIgnoreCase(type.subtype());
            if (!eventStream && startsWithJsonObject(source)) {
                ApiException e = RxResult.parseErrorBody(resp.code(), source.readUtf8(),
                        resp.headers().get(Headers.TRACE_ID), null);
                emitter.tryOnError(e);
                return;
            }
            pump(new AiSseReader(source), emitter, !eventStream);
        } catch (IOException e) {
            if (!emitter.isCancelled()) {
                emitter.tryOnError(ApiException.network(e));
            }
        } catch (JsonParseException | IllegalStateException e) {
            emitter.tryOnError(RxResult.toApiException(e));
        }
    }

    /** 逐个事件推送；replay 为真（Mock 整段返回）时 delta 之间稍作间隔 */
    @VisibleForTesting
    void pump(@NonNull AiSseReader reader, @NonNull FlowableEmitter<AiStreamEvent> emitter, boolean replay)
            throws IOException {
        AiSseEvent ev;
        while ((ev = reader.next()) != null) {
            if (emitter.isCancelled()) {
                return;
            }
            AiStreamEvent e = AiStreamEvent.parse(gson, ev);
            if (e == null) {
                continue;
            }
            if (replay && e.getType() == AiStreamEvent.Type.DELTA) {
                try {
                    Thread.sleep(MOCK_DELTA_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            emitter.onNext(e);
        }
        if (!emitter.isCancelled()) {
            emitter.onComplete();
        }
    }

    /** 跳过空白后第一个字符是“{”（开流前错误的 ApiResponse） */
    private static boolean startsWithJsonObject(@NonNull BufferedSource source) throws IOException {
        BufferedSource peek = source.peek();
        while (peek.request(1)) {
            byte b = peek.readByte();
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                continue;
            }
            return b == '{';
        }
        return false;
    }
}
