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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 语音转文字测试直连（模块11 K01，2026-10-22 用户确认）：阿里云百炼录音文件一次性识别，模型默认
 * {@link #DEFAULT_MODEL}（qwen3-asr-flash，可用 local.properties 的 ai.dashscopeAsrModel 覆盖），
 * 走 OpenAI 兼容的 Chat Completions：音频以 base64 data URL 放在 user 消息的 input_audio.data 中，
 * 识别文字取 choices[0].message.content（已实测）。官方限制单个音频 ≤10MB、≤5 分钟；K01 录音为 16kHz WAV、最长 60 秒（约 1.9MB）。
 * <p>
 * Key 与对话直连相同（BuildConfig.DASHSCOPE_API_KEY），release 包为空，{@link #isEnabled()} 为假，业务改走服务端 API-K10。
 * 日志只记模型、音频大小、状态码与耗时，不输出 Key 与音频。
 * <pre>
 * AiSpeechClient asr = AiSpeechClient.getDefault();
 * if (asr.isEnabled()) {
 *     asr.recognize(wavFile).subscribeOn(Schedulers.io())
 *             .subscribe(r -&gt; r.isRecognized() ? fill(r.getText()) : toast("没有听清，请再说一遍"), this::onError);
 * }
 * </pre>
 */
public class AiSpeechClient {

    private static final String TAG = "ZmtAiAsr";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** 百炼推荐的短音频一次性识别模型 */
    public static final String DEFAULT_MODEL = "qwen3-asr-flash";
    /** 官方单个音频上限 10MB（base64 前） */
    public static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private static volatile AiSpeechClient defaultClient;

    @NonNull
    private final OkHttpClient http;
    @Nullable
    private final AiProvider provider;

    /** 直连客户端（服务商来自 BuildConfig；release 包 Key 为空，isEnabled 为 false） */
    @NonNull
    public static AiSpeechClient getDefault() {
        AiSpeechClient c = defaultClient;
        if (c == null) {
            synchronized (AiSpeechClient.class) {
                c = defaultClient;
                if (c == null) {
                    AiProvider p = AiDirectConfig.dashscopeSpeech();
                    c = new AiSpeechClient(buildHttpClient(), p.isEnabled() ? p : null);
                    defaultClient = c;
                }
            }
        }
        return c;
    }

    public AiSpeechClient(@NonNull OkHttpClient http, @Nullable AiProvider provider) {
        this.http = http;
        this.provider = provider;
    }

    @NonNull
    public static OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public boolean isEnabled() {
        return provider != null && provider.isEnabled();
    }

    /** 识别一段录音文件（wav、m4a、mp3，按扩展名确定类型；在 io 线程订阅） */
    @NonNull
    public Single<AiSpeechResult> recognize(@NonNull File audio) {
        return Single.fromCallable(() -> {
            if (audio.length() > MAX_AUDIO_BYTES) {
                throw new AiException(AiException.Kind.CLIENT, -1, null, "录音文件过大", null);
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(audio.toPath());
            } catch (IOException e) {
                throw new AiException(AiException.Kind.CLIENT, -1, null, "无法读取录音文件", e);
            }
            return recognizeBlocking(bytes, mimeOf(audio.getName()));
        });
    }

    @WorkerThread
    @NonNull
    public AiSpeechResult recognizeBlocking(@NonNull byte[] audio, @NonNull String mime) throws AiException {
        AiProvider p = provider;
        if (p == null || !p.isEnabled()) {
            throw new AiException(AiException.Kind.DISABLED, -1, null, "语音识别直连未开启", null);
        }
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audio);
        Request req = new Request.Builder()
                .url(p.chatCompletionsUrl())
                .header("Authorization", "Bearer " + p.apiKey())
                .header("Accept", "application/json")
                .post(RequestBody.create(buildBody(p.getModel(), dataUrl).toString(), JSON))
                .build();
        long t0 = SystemClock.elapsedRealtime();
        Log.i(TAG, "识别请求 model=" + p.getModel() + " mime=" + mime + " bytes=" + audio.length);
        try (Response resp = http.newCall(req).execute()) {
            long cost = SystemClock.elapsedRealtime() - t0;
            ResponseBody rb = resp.body();
            String raw = rb == null ? "" : rb.string();
            Log.i(TAG, "识别响应 http=" + resp.code() + " costMs=" + cost);
            if (!resp.isSuccessful()) {
                throw new AiException(AiException.kindOf(resp.code()), resp.code(), p.getName(),
                        "语音识别服务返回 " + resp.code(), null);
            }
            return new AiSpeechResult(parseText(raw), p, cost);
        } catch (IOException e) {
            throw new AiException(AiException.Kind.NETWORK, -1, p.getName(), "网络错误或超时", e);
        }
    }

    // ===== 请求体与响应（单元测试） =====

    @VisibleForTesting
    @NonNull
    static JsonObject buildBody(@NonNull String model, @NonNull String audioDataUrl) {
        JsonObject audio = new JsonObject();
        audio.addProperty("data", audioDataUrl);
        JsonObject part = new JsonObject();
        part.addProperty("type", "input_audio");
        part.add("input_audio", audio);
        JsonArray content = new JsonArray();
        content.add(part);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(user);
        JsonObject options = new JsonObject();
        // 数字、日期按阿拉伯数字输出（“3 号育肥舍”“96 日龄”）
        options.addProperty("enable_itn", true);
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("messages", messages);
        root.addProperty("stream", false);
        root.add("asr_options", options);
        return root;
    }

    /** 取 choices[0].message.content（字符串或 text 片段数组）；没有识别出文字返回空串 */
    @VisibleForTesting
    @NonNull
    static String parseText(@NonNull String raw) throws AiException {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray choices = root.has("choices") && root.get("choices").isJsonArray()
                    ? root.getAsJsonArray("choices") : null;
            if (choices == null || choices.size() == 0) {
                return "";
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                return "";
            }
            JsonElement c = message.get("content");
            if (c.isJsonPrimitive()) {
                return c.getAsString().trim();
            }
            StringBuilder sb = new StringBuilder();
            if (c.isJsonArray()) {
                for (JsonElement e : c.getAsJsonArray()) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("text")) {
                        sb.append(e.getAsJsonObject().get("text").getAsString());
                    }
                }
            }
            return sb.toString().trim();
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, 200, null, "语音识别响应格式无法解析", e);
        }
    }

    @NonNull
    static String mimeOf(@NonNull String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".m4a") || n.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        return "audio/wav";
    }
}
