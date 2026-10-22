package com.zhimu.slmp.common.sms;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 短信验证码测试直连：直接调用阿里云号码认证服务（Dypnsapi 2017-05-25）的 SendSmsVerifyCode 与 CheckSmsVerifyCode，
 * V3 签名（{@link AcsV3Signer}），用独立的 OkHttpClient（不经过业务拦截器、Mock 与日志）。与业务无关，可复用。
 * <pre>
 * AliyunSmsClient sms = new AliyunSmsClient(AliyunSmsConfig.fromBuildConfig());
 * if (sms.isEnabled()) {
 *     sms.sendVerifyCode("13800006620", AliyunSmsTemplates.LOGIN_REGISTER);   // Single&lt;SmsSendResult&gt;
 *     sms.checkVerifyCode("13800006620", "123456");                         // Completable，不通过为 40103
 * }
 * </pre>
 * 验证码由阿里云生成：6 位数字，有效期 5 分钟，同一号码间隔 60 秒，再次发送使旧码失效。
 * 失败统一为 ApiException（{@link AliyunSmsErrors}）；网络失败为 code = -1。都在 io 线程执行。
 * 日志只记录接口名、返回码与 RequestId，不记录 Key、手机号与验证码。
 */
public class AliyunSmsClient {

    public static final String HOST = "dypnsapi.aliyuncs.com";
    public static final String API_VERSION = "2017-05-25";
    public static final String ACTION_SEND = "SendSmsVerifyCode";
    public static final String ACTION_CHECK = "CheckSmsVerifyCode";

    public static final int CODE_LENGTH = 6;
    public static final int VALID_SECONDS = 300;
    public static final int INTERVAL_SECONDS = 60;
    /** 由阿里云生成验证码，${min} 为有效分钟数 */
    public static final String TEMPLATE_PARAM = "{\"code\":\"##code##\",\"min\":\"" + (VALID_SECONDS / 60) + "\"}";

    static final String CODE_OK = "OK";
    static final String VERIFY_PASS = "PASS";
    private static final String TAG = "ZmtSms";

    @NonNull
    private final AliyunSmsConfig config;
    @Nullable
    private OkHttpClient http;
    @NonNull
    private final LongSupplier clock;
    @NonNull
    private final Supplier<String> nonce;
    /** 手机号 → 上次发送成功的时间，用于频率限制时给出剩余秒数（只在内存） */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public AliyunSmsClient(@NonNull AliyunSmsConfig config) {
        this(config, null, System::currentTimeMillis, AcsV3Signer::newNonce);
    }

    @VisibleForTesting
    AliyunSmsClient(@NonNull AliyunSmsConfig config, @Nullable OkHttpClient http, @NonNull LongSupplier clock,
                    @NonNull Supplier<String> nonce) {
        this.config = config;
        this.http = http;
        this.clock = clock;
        this.nonce = nonce;
    }

    /** 是否开启直连（debug 包且 AccessKey、签名都已配置） */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * SendSmsVerifyCode：向 phone 发送 templateCode 模板的验证码。
     *
     * @param phone        11 位手机号（不含空格）
     * @param templateCode {@link AliyunSmsTemplates} 中的系统模板
     */
    @NonNull
    public Single<SmsSendResult> sendVerifyCode(@NonNull String phone, @NonNull String templateCode) {
        return Single.fromCallable(() -> {
            Map<String, String> q = new TreeMap<>();
            q.put("PhoneNumber", phone);
            q.put("SignName", config.getSignName());
            q.put("TemplateCode", templateCode);
            q.put("TemplateParam", TEMPLATE_PARAM);
            q.put("CodeLength", String.valueOf(CODE_LENGTH));
            q.put("CodeType", "1");
            q.put("ValidTime", String.valueOf(VALID_SECONDS));
            q.put("Interval", String.valueOf(INTERVAL_SECONDS));
            q.put("DuplicatePolicy", "1");
            q.put("ReturnVerifyCode", "false");
            JsonObject resp = call(ACTION_SEND, q, cooldownForSend(phone));
            lastSentAt.put(phone, clock.getAsLong());
            JsonObject model = object(resp, "Model");
            return new SmsSendResult(model == null ? null : string(model, "BizId"), string(resp, "RequestId"),
                    INTERVAL_SECONDS, VALID_SECONDS);
        }).subscribeOn(Schedulers.io());
    }

    /** CheckSmsVerifyCode：VerifyResult 为 PASS 时完成，否则为 40103 */
    @NonNull
    public Completable checkVerifyCode(@NonNull String phone, @NonNull String verifyCode) {
        return Completable.fromAction(() -> {
            Map<String, String> q = new TreeMap<>();
            q.put("PhoneNumber", phone);
            q.put("VerifyCode", verifyCode);
            JsonObject resp = call(ACTION_CHECK, q, 0);
            JsonObject model = object(resp, "Model");
            String result = model == null ? null : string(model, "VerifyResult");
            if (!VERIFY_PASS.equals(result)) {
                throw AliyunSmsErrors.verifyFailed(string(resp, "RequestId"));
            }
        }).subscribeOn(Schedulers.io());
    }

    /** 发送被频率限制时的倒计时秒数：本机记录的剩余间隔，没有记录（如应用重启过）时按完整间隔 */
    @VisibleForTesting
    int cooldownForSend(@NonNull String phone) {
        Long last = lastSentAt.get(phone);
        if (last != null) {
            long left = INTERVAL_SECONDS - (clock.getAsLong() - last) / 1000L;
            if (left > 0) {
                return (int) left;
            }
        }
        return INTERVAL_SECONDS;
    }

    /** 签名并同步执行一次请求；返回码为 OK 时返回响应 JSON，否则抛出 ApiException */
    @NonNull
    private JsonObject call(@NonNull String action, @NonNull Map<String, String> query, int cooldownSeconds) {
        if (!config.isEnabled()) {
            throw new ApiException(ErrorCodes.SERVICE_UNAVAILABLE, AliyunSmsErrors.MSG_FAILED_PREFIX + "未配置直连");
        }
        AcsV3Signer.Signed signed = AcsV3Signer.sign(config.getAccessKeyId(), config.getAccessKeySecret(), "POST",
                HOST, action, API_VERSION, query, AcsV3Signer.formatDate(clock.getAsLong()), nonce.get());
        Request.Builder rb = new Request.Builder()
                .url("https://" + HOST + "/?" + signed.canonicalQuery)
                .post(RequestBody.create(new byte[0]));
        for (Map.Entry<String, String> h : signed.headers.entrySet()) {
            if (!"host".equals(h.getKey())) {
                rb.header(h.getKey(), h.getValue());
            }
        }
        int httpCode;
        String body;
        try (Response response = client().newCall(rb.build()).execute()) {
            httpCode = response.code();
            ResponseBody rbody = response.body();
            body = rbody == null ? "" : rbody.string();
        } catch (IOException e) {
            throw ApiException.network(e);
        }
        return parse(action, httpCode, body, cooldownSeconds);
    }

    /** 解析响应：Code 为 OK 时返回 JSON，否则按返回码转换为 ApiException 抛出 */
    @VisibleForTesting
    static JsonObject parse(@NonNull String action, int httpCode, @Nullable String body, int cooldownSeconds) {
        JsonObject resp = null;
        if (body != null && !body.isEmpty()) {
            try {
                JsonElement root = JsonParser.parseString(body);
                if (root.isJsonObject()) {
                    resp = root.getAsJsonObject();
                }
            } catch (RuntimeException ignored) {
                // 非 JSON 响应
            }
        }
        if (resp == null) {
            Log.w(TAG, action + " bad response, http=" + httpCode);
            throw AliyunSmsErrors.badResponse(httpCode, null);
        }
        String code = string(resp, "Code");
        String requestId = string(resp, "RequestId");
        if (CODE_OK.equals(code)) {
            return resp;
        }
        Log.w(TAG, action + " failed, http=" + httpCode + ", code=" + code + ", requestId=" + requestId);
        throw AliyunSmsErrors.toApiException(code, string(resp, "Message"), requestId, cooldownSeconds);
    }

    @NonNull
    private synchronized OkHttpClient client() {
        if (http == null) {
            http = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
        return http;
    }

    @Nullable
    private static JsonObject object(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    @Nullable
    private static String string(@NonNull JsonObject obj, @NonNull String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }
}
