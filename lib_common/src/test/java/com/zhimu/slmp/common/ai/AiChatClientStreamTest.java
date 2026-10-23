package com.zhimu.slmp.common.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 流式直连：按实测的千问 SSE 格式（每块 delta 同时带 content 与 reasoning_content、最后 usage 块与 data: [DONE]）
 * 推送 STARTED / THINKING / CONTENT / DONE；主用 503 换备用；已输出正文后中断不换备用；多轮历史与 stream=true 请求体。
 */
public class AiChatClientStreamTest {

    private static final String QWEN_SSE =
            "data: {\"choices\":[{\"delta\":{\"content\":\"\",\"role\":\"assistant\",\"reasoning_content\":\"\"},"
                    + "\"finish_reason\":null}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"用户问换料\"},"
                    + "\"finish_reason\":null}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"不需要全停，\",\"reasoning_content\":\"\"},"
                    + "\"finish_reason\":null}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"逐步换料。\",\"reasoning_content\":\"\"},"
                    + "\"finish_reason\":\"stop\"}]}\n\n"
                    + "data: {\"choices\":[],\"usage\":{\"completion_tokens\":457}}\n\n"
                    + "data: [DONE]\n\n";

    private static AiProvider provider(String name) {
        return new AiProvider(name, name, "https://" + name + ".example.com/v1", "m-" + name, "test-key");
    }

    /** 按服务商域名返回不同响应的假客户端 */
    private static OkHttpClient client(List<String> calls, String... hostAndResponse) {
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            String host = chain.request().url().host();
            calls.add(host);
            for (int i = 0; i < hostAndResponse.length; i += 2) {
                if (host.startsWith(hostAndResponse[i])) {
                    String spec = hostAndResponse[i + 1];
                    int code = spec.startsWith("HTTP ") ? Integer.parseInt(spec.substring(5, 8)) : 200;
                    String body = spec.startsWith("HTTP ") ? "{}" : spec;
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(code).message("x")
                            .body(ResponseBody.create(body, MediaType.get("text/event-stream")))
                            .build();
                }
            }
            throw new java.io.IOException("unexpected host " + host);
        }).build();
    }

    @Test
    public void stream_contentOnly_reasoningAsThinking() {
        List<String> calls = new ArrayList<>();
        AiChatClient c = new AiChatClient(client(calls, "primary", QWEN_SSE), Arrays.asList(provider("primary")));
        TestSubscriber<AiStreamChunk> ts = c.stream(AiChatRequest.builder().user("保育猪换料后拉稀？").build()).test();
        ts.assertComplete();
        List<AiStreamChunk> list = ts.values();
        assertEquals(AiStreamChunk.Type.STARTED, list.get(0).getType());
        assertEquals(AiStreamChunk.Type.THINKING, list.get(1).getType());
        assertEquals(AiStreamChunk.Type.CONTENT, list.get(2).getType());
        assertEquals("不需要全停，", list.get(2).getText());
        assertEquals("逐步换料。", list.get(3).getText());
        AiStreamChunk done = list.get(list.size() - 1);
        assertEquals(AiStreamChunk.Type.DONE, done.getType());
        assertEquals("不需要全停，逐步换料。", done.getText());
        assertEquals("stop", done.getFinishReason());
        assertFalse(done.isFallback());
        assertEquals(5, list.size());
    }

    @Test
    public void stream_primary503_fallsBackBeforeContent() {
        List<String> calls = new ArrayList<>();
        AiChatClient c = new AiChatClient(client(calls, "primary", "HTTP 503", "backup", QWEN_SSE),
                Arrays.asList(provider("primary"), provider("backup")));
        TestSubscriber<AiStreamChunk> ts = c.stream(AiChatRequest.builder().user("q").build()).test();
        ts.assertComplete();
        assertEquals(Arrays.asList("primary.example.com", "backup.example.com"), calls);
        AiStreamChunk started = ts.values().get(0);
        assertEquals(AiStreamChunk.Type.STARTED, started.getType());
        assertTrue(started.isFallback());
        assertEquals("backup", started.getProvider().getName());
    }

    @Test
    public void stream_interruptedAfterContent_noFallback() {
        String truncated = "data: {\"choices\":[{\"delta\":{\"content\":\"已经说了一半\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"con";
        List<String> calls = new ArrayList<>();
        AiChatClient c = new AiChatClient(client(calls, "primary", truncated, "backup", QWEN_SSE),
                Arrays.asList(provider("primary"), provider("backup")));
        TestSubscriber<AiStreamChunk> ts = c.stream(AiChatRequest.builder().user("q").build()).test();
        ts.assertError(e -> e instanceof AiException);
        assertEquals(1, calls.size());
        assertEquals("已经说了一半", ts.values().get(1).getText());
    }

    @Test
    public void stream_emptyAnswer_isRetryableServerError() {
        String empty = "data: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"想\"},"
                + "\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n";
        List<String> calls = new ArrayList<>();
        AiChatClient c = new AiChatClient(client(calls, "primary", empty), Arrays.asList(provider("primary")));
        TestSubscriber<AiStreamChunk> ts = c.stream(AiChatRequest.builder().user("q").build()).test();
        ts.assertError(e -> e instanceof AiException && ((AiException) e).getKind() == AiException.Kind.SERVER);
    }

    @Test
    public void stream_disabledWithoutProviders() {
        AiChatClient c = new AiChatClient(new OkHttpClient(), new ArrayList<>());
        c.stream(AiChatRequest.builder().user("q").build()).test()
                .assertError(e -> ((AiException) e).getKind() == AiException.Kind.DISABLED);
    }

    @Test
    public void parseStreamDelta_usageAndError() {
        AiChatClient.StreamDelta usage = AiChatClient.parseStreamDelta("{\"choices\":[],\"usage\":{}}");
        assertNull(usage.content);
        assertFalse(usage.reasoning);
        AiChatClient.StreamDelta err = AiChatClient.parseStreamDelta("{\"error\":{\"code\":\"DataInspectionFailed\"}}");
        assertEquals("DataInspectionFailed", err.error);
    }

    @Test
    public void buildBody_streamWithHistory() {
        AiChatRequest req = AiChatRequest.builder().system("你是养殖助手")
                .history(Arrays.asList(AiTurn.user("上一问"), AiTurn.assistant("上一答")))
                .user("这一问").build();
        JsonObject body = AiChatClient.buildBody("qwen3.8-max", req, true);
        assertTrue(body.get("stream").getAsBoolean());
        JsonArray messages = body.getAsJsonArray("messages");
        assertEquals(4, messages.size());
        assertEquals("assistant", messages.get(2).getAsJsonObject().get("role").getAsString());
        assertEquals("这一问", messages.get(3).getAsJsonObject().get("content").getAsString());
        // 原有单轮调用不受影响
        assertFalse(AiChatClient.buildBody("m", AiChatRequest.builder().user("x").build()).get("stream").getAsBoolean());
    }
}
