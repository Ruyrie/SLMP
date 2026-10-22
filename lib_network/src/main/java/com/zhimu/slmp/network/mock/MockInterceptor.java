package com.zhimu.slmp.network.mock;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ErrorCodes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * 示例数据拦截器（只在 BuildConfig.DEBUG && BuildConfig.USE_MOCK 时加入，作为最后一个应用拦截器）：
 * 按“方法 + 路径 + 条件”匹配 assets/mock 下的规则，返回响应文件；HTTP 状态取 code 前三位（0 为 200）。
 * 没有匹配规则时返回 code 50001、message“Mock 未配置：方法 路径”，并打印日志。
 * 注意：Mock 响应不经过 TokenAuthenticator（40101 刷新流程只在真实服务端下验证）。
 */
public class MockInterceptor implements Interceptor {

    private static final String TAG = "ZmtMock";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_BODY = 1024 * 1024;

    private final MockRouter router;

    public MockInterceptor(@NonNull MockSource source) {
        this.router = new MockRouter(source);
    }

    public MockInterceptor(@NonNull MockRouter router) {
        this.router = router;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        List<String> segments = url.pathSegments();
        Map<String, String> query = new HashMap<>();
        for (String name : url.queryParameterNames()) {
            String v = url.queryParameter(name);
            query.put(name, v == null ? "" : v);
        }
        Map<String, String> headers = new HashMap<>();
        for (String name : request.headers().names()) {
            headers.put(name, request.header(name));
        }
        String body = bodyOf(request.body());
        String path = url.encodedPath();

        MockRouter.Match match = router.match(request.method(), segments, query, headers, body);
        String json;
        long delay = MockRule.DEFAULT_DELAY_MS;
        if (match == null) {
            Log.w(TAG, "Mock 未配置：" + request.method() + " " + path);
            json = notConfigured(request.method(), path);
        } else {
            json = router.getSource().read(match.rule.dir, match.rule.file);
            delay = match.rule.delayMs;
            if (json == null) {
                Log.w(TAG, "Mock 响应文件不存在：mock/" + match.rule.dir + "/" + match.rule.file);
                json = notConfigured(request.method(), path);
            } else {
                Log.d(TAG, request.method() + " " + path + " -> mock/" + match.rule.dir + "/" + match.rule.file);
            }
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Mock 请求已取消", e);
            }
        }
        int status = ErrorCodes.httpStatusOf(codeOf(json));
        Response.Builder b = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(status == 200 ? "OK" : "Mock")
                .header("Content-Type", JSON.toString())
                .body(ResponseBody.create(json, JSON));
        String trace = traceOf(json);
        if (trace != null) {
            b.header("X-Trace-Id", trace);
        }
        return b.build();
    }

    @NonNull
    static String notConfigured(@NonNull String method, @NonNull String path) {
        JsonObject o = new JsonObject();
        o.addProperty("code", ErrorCodes.SERVER_ERROR);
        o.addProperty("message", "Mock 未配置：" + method + " " + path);
        o.add("data", null);
        o.addProperty("trace_id", "mock-none");
        return o.toString();
    }

    private static int codeOf(@NonNull String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (e.isJsonObject() && e.getAsJsonObject().has("code")) {
                return e.getAsJsonObject().get("code").getAsInt();
            }
        } catch (RuntimeException ignored) {
            // 响应文件不是 ApiResponse 时按成功返回
        }
        return 0;
    }

    private static String traceOf(@NonNull String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (e.isJsonObject() && e.getAsJsonObject().has("trace_id")) {
                return e.getAsJsonObject().get("trace_id").getAsString();
            }
        } catch (RuntimeException ignored) {
            // 忽略
        }
        return null;
    }

    @NonNull
    private static String bodyOf(RequestBody body) throws IOException {
        if (body == null) {
            return "";
        }
        MediaType type = body.contentType();
        if (type != null && "multipart".equals(type.type())) {
            return "";
        }
        long len = body.contentLength();
        if (len > MAX_BODY) {
            return "";
        }
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readUtf8();
    }
}
