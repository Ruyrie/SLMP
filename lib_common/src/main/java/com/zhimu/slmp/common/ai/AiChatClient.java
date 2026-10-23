package com.zhimu.slmp.common.ai;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * AI 测试直连客户端（模块07 AI 诊断先用，模块11 AI 问答也可复用）：OpenAI 兼容的 Chat Completions，
 * 按 {@link AiDirectConfig#enabledProviders()} 的顺序调用——主用阿里云百炼，网络错误、超时、5xx、429 或回答为空时
 * 换 DeepSeek。图片以 base64 data URL 放在 user 消息的 image_url 中；两家都是推理模型，只取 content，
 * 忽略 reasoning_content；读取超时 150 秒。
 * <p>
 * 使用独立的 OkHttpClient（不经过 App 的鉴权、Mock 拦截器），日志只记服务商、模型、图片数量与大小、状态码和耗时，
 * 不输出 Key 与请求体。
 * <pre>
 * AiChatClient ai = AiChatClient.getDefault();
 * if (ai.isEnabled()) {
 *     ai.chat(AiChatRequest.builder().system(prompt).user(text).images(frames).build())
 *             .subscribeOn(Schedulers.io())
 *             .subscribe(result -&gt; show(result.getContent()), e -&gt; fallbackToServer());
 * }
 * </pre>
 * 流式（模块11 K01，stream=true）：{@link #stream(AiChatRequest)} 返回 {@code Flowable<AiStreamChunk>}，
 * 依次推送 STARTED、THINKING（推理中，不含内容）、CONTENT（正文增量）、DONE（完整正文）；取消订阅即取消请求。
 * 还没收到任何正文时，网络错误、超时、5xx、429、回答为空会换备用服务商；已输出正文后失败以
 * {@link AiException.Kind#NETWORK} 结束（由调用方显示“回答中断”），不再重试。
 * <pre>
 * disposable = ai.stream(AiChatRequest.builder().system(prompt).history(turns).user(text).build())
 *         .subscribeOn(Schedulers.io())
 *         .observeOn(AndroidSchedulers.mainThread())
 *         .subscribe(chunk -&gt; { if (chunk.getType() == AiStreamChunk.Type.CONTENT) append(chunk.getText()); },
 *                 e -&gt; showInterrupted());
 * </pre>
 */
public class AiChatClient {

    private static final String TAG = "ZmtAi";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static final long CONNECT_TIMEOUT_SEC = 15;
    public static final long READ_TIMEOUT_SEC = 150;
    public static final long CALL_TIMEOUT_SEC = 180;

    private static volatile AiChatClient defaultClient;

    @NonNull
    private final OkHttpClient http;
    @NonNull
    private final List<AiProvider> providers;

    /** 直连客户端（服务商来自 BuildConfig；release 包为空，isEnabled 为 false） */
    @NonNull
    public static AiChatClient getDefault() {
        AiChatClient c = defaultClient;
        if (c == null) {
            synchronized (AiChatClient.class) {
                c = defaultClient;
                if (c == null) {
                    c = new AiChatClient(buildHttpClient(), AiDirectConfig.enabledProviders());
                    defaultClient = c;
                }
            }
        }
        return c;
    }

    public AiChatClient(@NonNull OkHttpClient http, @NonNull List<AiProvider> providers) {
        this.http = http;
        this.providers = Collections.unmodifiableList(providers);
    }

    @NonNull
    public static OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public boolean isEnabled() {
        return !providers.isEmpty();
    }

    @NonNull
    public List<AiProvider> getProviders() {
        return providers;
    }

    /** 发送一次对话（在 io 线程订阅）；全部服务商都失败时以最后一个 {@link AiException} 结束 */
    @NonNull
    public Single<AiChatResult> chat(@NonNull AiChatRequest request) {
        return Single.fromCallable(() -> chatBlocking(request));
    }

    @WorkerThread
    @NonNull
    public AiChatResult chatBlocking(@NonNull AiChatRequest request) throws AiException {
        if (providers.isEmpty()) {
            throw new AiException(AiException.Kind.DISABLED, -1, null, "AI 直连未开启", null);
        }
        AiException last = null;
        for (int i = 0; i < providers.size(); i++) {
            AiProvider p = providers.get(i);
            try {
                return call(p, request, i > 0);
            } catch (AiException e) {
                last = e;
                Log.w(TAG, "直连失败 provider=" + p + " kind=" + e.getKind() + " http=" + e.getHttpCode()
                        + (e.isRetryable() && i + 1 < providers.size() ? "，改用备用服务商" : ""));
                if (!e.isRetryable()) {
                    throw e;
                }
            }
        }
        throw last;
    }

    @NonNull
    private AiChatResult call(@NonNull AiProvider p, @NonNull AiChatRequest req, boolean fallback) throws AiException {
        String body = buildBody(p.getModel(), req).toString();
        Request httpReq = new Request.Builder()
                .url(p.chatCompletionsUrl())
                .header("Authorization", "Bearer " + p.apiKey())
                .header("Accept", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();
        long t0 = SystemClock.elapsedRealtime();
        Log.i(TAG, "请求 tag=" + req.getTag() + " provider=" + p + " images=" + req.getImages().size()
                + " imageBytes=" + req.imageBytes() + " textChars=" + req.getUser().length());
        try (Response resp = http.newCall(httpReq).execute()) {
            long cost = SystemClock.elapsedRealtime() - t0;
            ResponseBody rb = resp.body();
            String raw = rb == null ? "" : rb.string();
            Log.i(TAG, "响应 provider=" + p + " http=" + resp.code() + " costMs=" + cost + " bytes=" + raw.length());
            if (!resp.isSuccessful()) {
                throw new AiException(AiException.kindOf(resp.code()), resp.code(), p.getName(),
                        "AI 服务返回 " + resp.code(), null);
            }
            Parsed parsed = parseResponse(raw);
            if (parsed.content == null || parsed.content.trim().isEmpty()) {
                // 推理用尽输出长度或被截断：换备用服务商再试
                throw new AiException(AiException.Kind.SERVER, resp.code(), p.getName(),
                        "AI 回答为空（finish_reason=" + parsed.finishReason + "）", null);
            }
            return new AiChatResult(parsed.content.trim(), p, parsed.finishReason, cost, fallback);
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NETWORK, -1, p.getName(), "网络错误或超时", e);
        }
    }

    // ===== 流式（模块11 K01） =====

    /**
     * 流式对话（在 io 线程订阅）：stream=true，按行读取 SSE，只把 content 作为正文推送，reasoning_content 只发 THINKING。
     * 全部服务商都失败时以最后一个 {@link AiException} 结束；直连未开启时为 {@link AiException.Kind#DISABLED}。
     */
    @NonNull
    public Flowable<AiStreamChunk> stream(@NonNull AiChatRequest request) {
        return Flowable.create(emitter -> streamBlocking(request, emitter), BackpressureStrategy.BUFFER);
    }

    @WorkerThread
    private void streamBlocking(@NonNull AiChatRequest request, @NonNull FlowableEmitter<AiStreamChunk> emitter) {
        if (providers.isEmpty()) {
            emitter.tryOnError(new AiException(AiException.Kind.DISABLED, -1, null, "AI 直连未开启", null));
            return;
        }
        AiException last = null;
        for (int i = 0; i < providers.size(); i++) {
            AiProvider p = providers.get(i);
            boolean[] contentSent = {false};
            try {
                streamOne(p, request, i > 0, emitter, contentSent);
                if (!emitter.isCancelled()) {
                    emitter.onComplete();
                }
                return;
            } catch (AiException e) {
                if (emitter.isCancelled()) {
                    return;
                }
                last = e;
                boolean next = e.isRetryable() && !contentSent[0] && i + 1 < providers.size();
                Log.w(TAG, "流式直连失败 provider=" + p + " kind=" + e.getKind() + " http=" + e.getHttpCode()
                        + " contentSent=" + contentSent[0] + (next ? "，改用备用服务商" : ""));
                if (!next) {
                    emitter.tryOnError(e);
                    return;
                }
            }
        }
        emitter.tryOnError(last);
    }

    private void streamOne(@NonNull AiProvider p, @NonNull AiChatRequest req, boolean fallback,
                           @NonNull FlowableEmitter<AiStreamChunk> emitter, @NonNull boolean[] contentSent)
            throws AiException {
        String body = buildBody(p.getModel(), req, true).toString();
        Request httpReq = new Request.Builder()
                .url(p.chatCompletionsUrl())
                .header("Authorization", "Bearer " + p.apiKey())
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body, JSON))
                .build();
        Call call = http.newCall(httpReq);
        emitter.setCancellable(call::cancel);
        long t0 = SystemClock.elapsedRealtime();
        Log.i(TAG, "流式请求 tag=" + req.getTag() + " provider=" + p + " images=" + req.getImages().size()
                + " imageBytes=" + req.imageBytes() + " history=" + req.getHistory().size()
                + " textChars=" + req.getUser().length());
        try (Response resp = call.execute()) {
            if (!resp.isSuccessful()) {
                Log.i(TAG, "流式响应 provider=" + p + " http=" + resp.code());
                throw new AiException(AiException.kindOf(resp.code()), resp.code(), p.getName(),
                        "AI 服务返回 " + resp.code(), null);
            }
            ResponseBody rb = resp.body();
            if (rb == null) {
                throw new AiException(AiException.Kind.BAD_RESPONSE, resp.code(), p.getName(), "AI 响应为空", null);
            }
            emitter.onNext(AiStreamChunk.started(p, fallback));
            AiSseReader reader = new AiSseReader(rb.source());
            StringBuilder all = new StringBuilder();
            String finish = null;
            AiSseEvent ev;
            while ((ev = reader.next()) != null) {
                if (emitter.isCancelled()) {
                    return;
                }
                if (ev.isComment()) {
                    continue;
                }
                if (ev.isDoneMarker()) {
                    break;
                }
                StreamDelta d = parseStreamDelta(ev.getData());
                if (d.error != null) {
                    throw new AiException(AiException.Kind.SERVER, resp.code(), p.getName(), "AI 服务出错：" + d.error,
                            null);
                }
                if (d.reasoning) {
                    emitter.onNext(AiStreamChunk.thinking(p, fallback));
                }
                if (d.content != null && !d.content.isEmpty()) {
                    contentSent[0] = true;
                    all.append(d.content);
                    emitter.onNext(AiStreamChunk.content(d.content, p, fallback));
                }
                if (d.finishReason != null) {
                    finish = d.finishReason;
                }
            }
            long cost = SystemClock.elapsedRealtime() - t0;
            Log.i(TAG, "流式结束 provider=" + p + " costMs=" + cost + " chars=" + all.length() + " finish=" + finish);
            if (all.toString().trim().isEmpty()) {
                throw new AiException(AiException.Kind.SERVER, resp.code(), p.getName(),
                        "AI 回答为空（finish_reason=" + finish + "）", null);
            }
            emitter.onNext(AiStreamChunk.done(all.toString(), p, fallback, finish, cost));
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NETWORK, -1, p.getName(), "网络错误或超时", e);
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, 200, p.getName(), "AI 响应格式无法解析", e);
        }
    }

    /** 流式响应中一个 data 块的解析结果 */
    static final class StreamDelta {
        @Nullable
        final String content;
        /** 本块带有非空 reasoning_content（推理中） */
        final boolean reasoning;
        @Nullable
        final String finishReason;
        /** 流中途的错误对象（{"error": {...}}）说明 */
        @Nullable
        final String error;

        StreamDelta(@Nullable String content, boolean reasoning, @Nullable String finishReason,
                    @Nullable String error) {
            this.content = content;
            this.reasoning = reasoning;
            this.finishReason = finishReason;
            this.error = error;
        }
    }

    /**
     * 解析一个 chat.completion.chunk：取 choices[0].delta.content 与 finish_reason，reasoning_content 只记“推理中”；
     * 最后的 usage 块（choices 为空）返回空结果。
     */
    @VisibleForTesting
    @NonNull
    static StreamDelta parseStreamDelta(@NonNull String data) {
        JsonObject root = JsonParser.parseString(data).getAsJsonObject();
        if (root.has("error") && root.get("error").isJsonObject()) {
            JsonObject err = root.getAsJsonObject("error");
            String msg = err.has("code") && !err.get("code").isJsonNull() ? err.get("code").getAsString() : "error";
            return new StreamDelta(null, false, null, msg);
        }
        JsonArray choices = root.has("choices") && root.get("choices").isJsonArray()
                ? root.getAsJsonArray("choices") : null;
        if (choices == null || choices.size() == 0) {
            return new StreamDelta(null, false, null, null);
        }
        JsonObject first = choices.get(0).getAsJsonObject();
        String finish = first.has("finish_reason") && !first.get("finish_reason").isJsonNull()
                ? first.get("finish_reason").getAsString() : null;
        JsonObject delta = first.has("delta") && first.get("delta").isJsonObject()
                ? first.getAsJsonObject("delta") : null;
        if (delta == null) {
            return new StreamDelta(null, false, finish, null);
        }
        String content = delta.has("content") && delta.get("content").isJsonPrimitive()
                ? delta.get("content").getAsString() : null;
        boolean reasoning = delta.has("reasoning_content") && delta.get("reasoning_content").isJsonPrimitive()
                && !delta.get("reasoning_content").getAsString().isEmpty();
        return new StreamDelta(content, reasoning, finish, null);
    }

    // ===== 请求体与响应（单元测试） =====

    @VisibleForTesting
    @NonNull
    static JsonObject buildBody(@NonNull String model, @NonNull AiChatRequest req) {
        return buildBody(model, req, false);
    }

    @VisibleForTesting
    @NonNull
    static JsonObject buildBody(@NonNull String model, @NonNull AiChatRequest req, boolean stream) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        JsonArray messages = new JsonArray();
        if (req.getSystem() != null && !req.getSystem().isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", req.getSystem());
            messages.add(sys);
        }
        for (AiTurn turn : req.getHistory()) {
            JsonObject m = new JsonObject();
            m.addProperty("role", turn.getRole());
            m.addProperty("content", turn.getContent());
            messages.add(m);
        }
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        if (req.getImages().isEmpty()) {
            user.addProperty("content", req.getUser());
        } else {
            JsonArray parts = new JsonArray();
            parts.add(textPart(req.getUser()));
            for (AiImage image : req.getImages()) {
                parts.add(textPart(image.getLabel()));
                JsonObject url = new JsonObject();
                url.addProperty("url", image.getDataUrl());
                JsonObject part = new JsonObject();
                part.addProperty("type", "image_url");
                part.add("image_url", url);
                parts.add(part);
            }
            user.add("content", parts);
        }
        messages.add(user);
        root.add("messages", messages);
        root.addProperty("max_tokens", req.getMaxTokens());
        root.addProperty("stream", stream);
        if (req.getTemperature() != null) {
            root.addProperty("temperature", req.getTemperature());
        }
        return root;
    }

    @NonNull
    private static JsonObject textPart(@NonNull String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "text");
        o.addProperty("text", text);
        return o;
    }

    static final class Parsed {
        @Nullable
        final String content;
        @Nullable
        final String finishReason;

        Parsed(@Nullable String content, @Nullable String finishReason) {
            this.content = content;
            this.finishReason = finishReason;
        }
    }

    /** 取 choices[0].message.content（字符串或 text 片段数组），忽略 reasoning_content */
    @VisibleForTesting
    @NonNull
    static Parsed parseResponse(@NonNull String raw) throws AiException {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray choices = root.has("choices") && root.get("choices").isJsonArray()
                    ? root.getAsJsonArray("choices") : null;
            if (choices == null || choices.size() == 0) {
                return new Parsed(null, null);
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            String finish = first.has("finish_reason") && !first.get("finish_reason").isJsonNull()
                    ? first.get("finish_reason").getAsString() : null;
            JsonObject message = first.has("message") && first.get("message").isJsonObject()
                    ? first.getAsJsonObject("message") : null;
            if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                return new Parsed(null, finish);
            }
            JsonElement c = message.get("content");
            if (c.isJsonPrimitive()) {
                return new Parsed(c.getAsString(), finish);
            }
            if (c.isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement part : c.getAsJsonArray()) {
                    if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                        sb.append(part.getAsJsonObject().get("text").getAsString());
                    } else if (part.isJsonPrimitive()) {
                        sb.append(part.getAsString());
                    }
                }
                return new Parsed(sb.toString(), finish);
            }
            return new Parsed(null, finish);
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, 200, null, "AI 响应格式无法解析", e);
        }
    }
}
