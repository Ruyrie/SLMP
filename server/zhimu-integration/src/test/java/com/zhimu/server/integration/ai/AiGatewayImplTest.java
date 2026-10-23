package com.zhimu.server.integration.ai;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.support.FakeHttpServer;
import com.zhimu.server.integration.support.Jsons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 网关：模型路由、主备切换、超时、并发、流式回调与取消（本地假服务，不访问网络）。
 */
class AiGatewayImplTest {

    private static final String OK_BODY = "{\"id\":\"x\",\"model\":\"qwen3.8-max\",\"choices\":[{\"index\":0,"
            + "\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"先想一想\","
            + "\"content\":\"  建议先隔离观察。 \"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":34,"
            + "\"total_tokens\":46}}";
    private static final String FALLBACK_BODY = "{\"choices\":[{\"finish_reason\":\"stop\","
            + "\"message\":{\"content\":\"备用回答\"}}]}";

    private static final List<String> QWEN_SSE = List.of(
            "data: {\"model\":\"qwen3.8-max\",\"choices\":[{\"delta\":{\"content\":\"\",\"role\":\"assistant\","
                    + "\"reasoning_content\":\"\"},\"finish_reason\":null}]}\n\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"用户问换料\"},"
                    + "\"finish_reason\":null}]}\n\n",
            ": keep-alive\n\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"不需要全停，\",\"reasoning_content\":\"\"},"
                    + "\"finish_reason\":null}]}\n\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"逐步换料。\",\"reasoning_content\":\"\"},"
                    + "\"finish_reason\":\"stop\"}]}\n\n",
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":457,\"total_tokens\":477}}\n\n",
            "data: [DONE]\n\n");

    private FakeHttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = FakeHttpServer.start((r, ex) -> FakeHttpServer.json(ex, 500, "{}"));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private AiProperties props() {
        AiProperties p = new AiProperties();
        p.getPrimary().setName("dashscope");
        p.getPrimary().setBaseUrl(server.url() + "/p/");
        p.getPrimary().setApiKey("test-primary-key");
        p.getPrimary().setChatModel("qwen-chat");
        p.getPrimary().setVisionModel("qwen-vl");
        p.getPrimary().setAsrModel("qwen3-asr-flash");
        p.getFallback().setName("deepseek");
        p.getFallback().setBaseUrl(server.url() + "/f");
        p.getFallback().setApiKey("test-fallback-key");
        p.getFallback().setChatModel("ds-chat");
        p.getFallback().setVisionModel("ds-vl");
        return p;
    }

    private static AiChatRequest textRequest() {
        return AiChatRequest.builder().scene(AiScene.CHAT)
                .message(AiMessage.system("你是养殖助手"))
                .message(AiMessage.user("保育猪拉稀怎么办？"))
                .build();
    }

    /** 主备按路径前缀返回不同响应 */
    private void route(FakeHttpServer.Handler primary, FakeHttpServer.Handler fallback) {
        server.handler((r, ex) -> {
            if (r.path().startsWith("/p/")) {
                primary.handle(r, ex);
            } else {
                fallback.handle(r, ex);
            }
        });
    }

    // ===== 非流式 =====

    @Test
    void chat_primarySuccess_ignoresReasoningAndReturnsUsage() {
        route((r, ex) -> FakeHttpServer.json(ex, 200, OK_BODY), (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        AiGatewayImpl gw = new AiGatewayImpl(props());

        AiChatResult result = gw.chat(textRequest());

        assertThat(result.getContent()).isEqualTo("建议先隔离观察。");
        assertThat(result.getProvider()).isEqualTo("dashscope");
        assertThat(result.getModel()).isEqualTo("qwen3.8-max");
        assertThat(result.isFallback()).isFalse();
        assertThat(result.getFinishReason()).isEqualTo("stop");
        assertThat(result.getUsage()).isEqualTo(new AiUsage(12, 34, 46));

        FakeHttpServer.Recorded req = server.requests().getFirst();
        assertThat(req.path()).isEqualTo("/p/chat/completions");
        assertThat(req.header("Authorization")).isEqualTo("Bearer test-primary-key");
        JsonNode body = Jsons.parse(req.body());
        assertThat(body.get("model").asString()).isEqualTo("qwen-chat");
        assertThat(body.get("stream").asBoolean()).isFalse();
        assertThat(body.get("messages").size()).isEqualTo(2);
        assertThat(body.get("messages").get(0).get("role").asString()).isEqualTo("system");
        assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("保育猪拉稀怎么办？");
        assertThat(body.has("response_format")).isFalse();
        assertThat(body.has("max_tokens")).isFalse();
        assertThat(body.has("temperature")).isFalse();
    }

    @Test
    void chat_withImages_usesVisionModel_jsonModeAndOptions() {
        route((r, ex) -> FakeHttpServer.json(ex, 200, OK_BODY), (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        AiGatewayImpl gw = new AiGatewayImpl(props());

        gw.chat(AiChatRequest.builder().scene(AiScene.DIAGNOSIS)
                .message(AiMessage.system("只输出 JSON"))
                .message(AiMessage.user("看看这头猪", List.of("data:image/png;base64,AAAA", "https://x.example/a.jpg")))
                .jsonMode(true).maxTokens(2000).temperature(0.2)
                .build());

        JsonNode body = Jsons.parse(server.requests().getFirst().body());
        assertThat(body.get("model").asString()).isEqualTo("qwen-vl");
        JsonNode parts = body.get("messages").get(1).get("content");
        assertThat(parts.size()).isEqualTo(3);
        assertThat(parts.get(0).get("type").asString()).isEqualTo("text");
        assertThat(parts.get(1).get("image_url").get("url").asString()).isEqualTo("data:image/png;base64,AAAA");
        assertThat(parts.get(2).get("image_url").get("url").asString()).isEqualTo("https://x.example/a.jpg");
        assertThat(body.get("response_format").get("type").asString()).isEqualTo("json_object");
        assertThat(body.get("max_tokens").asInt()).isEqualTo(2000);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    void chat_primaryRetryableFailures_fallBack() {
        for (int status : new int[]{503, 429}) {
            route((r, ex) -> FakeHttpServer.json(ex, status, "{\"error\":{\"code\":\"busy\",\"message\":\"x\"}}"),
                    (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
            AiChatResult result = new AiGatewayImpl(props()).chat(textRequest());
            assertThat(result.getContent()).isEqualTo("备用回答");
            assertThat(result.isFallback()).isTrue();
            assertThat(result.getProvider()).isEqualTo("deepseek");
            assertThat(result.getModel()).isEqualTo("ds-chat");
        }
        // 空回答（推理用尽长度）也切换
        route((r, ex) -> FakeHttpServer.json(ex, 200, "{\"choices\":[{\"finish_reason\":\"length\","
                + "\"message\":{\"content\":\"\",\"reasoning_content\":\"想了很久\"}}]}"),
                (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        assertThat(new AiGatewayImpl(props()).chat(textRequest()).isFallback()).isTrue();
    }

    @Test
    void chat_networkError_fallsBack() {
        AiProperties p = props();
        p.getPrimary().setBaseUrl("http://127.0.0.1:1/v1");
        route((r, ex) -> FakeHttpServer.json(ex, 500, "{}"), (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        AiChatResult result = new AiGatewayImpl(p).chat(textRequest());
        assertThat(result.isFallback()).isTrue();
    }

    @Test
    void chat_primaryClientError_noFallback() {
        route((r, ex) -> FakeHttpServer.json(ex, 400, "{\"error\":{\"code\":\"DataInspectionFailed\"}}"),
                (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        assertThatThrownBy(() -> new AiGatewayImpl(props()).chat(textRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50201));
        assertThat(server.requests("/f")).isEmpty();
    }

    @Test
    void chat_bothFail_50201() {
        route((r, ex) -> FakeHttpServer.json(ex, 502, "bad gateway"), (r, ex) -> FakeHttpServer.json(ex, 503, "{}"));
        assertThatThrownBy(() -> new AiGatewayImpl(props()).chat(textRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50201));
        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void chat_timeout_50401_withoutFallback() {
        route((r, ex) -> {
            Thread.sleep(2000);
            FakeHttpServer.json(ex, 200, OK_BODY);
        }, (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        AiChatRequest req = AiChatRequest.builder().message(AiMessage.user("q")).timeout(Duration.ofMillis(500)).build();
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> new AiGatewayImpl(props()).chat(req))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50401));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)).isLessThan(1800);
        assertThat(server.requests("/f")).isEmpty();
    }

    @Test
    void notConfigured_disabledAndFails50201() throws Exception {
        AiProperties p = new AiProperties();
        AiGatewayImpl gw = new AiGatewayImpl(p);
        assertThat(gw.isEnabled()).isFalse();
        assertThatThrownBy(() -> gw.chat(textRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50201));
        assertThatThrownBy(() -> gw.recognizeSpeech(new byte[]{1}, "audio/wav"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50201));
        Recorder rec = new Recorder();
        AiStreamHandle h = gw.stream(textRequest(), rec);
        assertThat(h.isDone()).isTrue();
        assertThat(rec.error.getCode()).isEqualTo(50201);

        AiProperties off = props();
        off.setEnabled(false);
        assertThat(new AiGatewayImpl(off).isEnabled()).isFalse();
    }

    @Test
    void concurrencyLimit_waitTimeout50201_andPermitReleasedAfterCancel() throws Exception {
        route((r, ex) -> {
            if (r.body().contains("\"stream\":true")) {
                FakeHttpServer.sse(ex, repeat("data: {\"choices\":[{\"delta\":{\"content\":\"字\"}}]}\n\n", 100), 100);
            } else {
                FakeHttpServer.json(ex, 200, OK_BODY);
            }
        }, (r, ex) -> FakeHttpServer.json(ex, 200, FALLBACK_BODY));
        AiProperties p = props();
        p.setMaxConcurrency(1);
        AiGatewayImpl gw = new AiGatewayImpl(p, Duration.ofMillis(300));

        Recorder rec = new Recorder();
        AiStreamHandle handle = gw.stream(textRequest(), rec);
        assertThat(rec.firstDelta.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> gw.chat(textRequest()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50201));

        handle.cancel();
        assertThat(handle.isDone()).isTrue();
        assertThat(gw.chat(textRequest()).getContent()).isEqualTo("建议先隔离观察。");
    }

    // ===== 流式 =====

    @Test
    void stream_success_deltasStartUsageAndComplete() throws Exception {
        route((r, ex) -> FakeHttpServer.sse(ex, QWEN_SSE, 5), (r, ex) -> FakeHttpServer.json(ex, 500, "{}"));
        Recorder rec = new Recorder();
        AiStreamHandle handle = new AiGatewayImpl(props()).stream(textRequest(), rec);

        assertThat(rec.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.error).isNull();
        assertThat(rec.deltas).containsExactly("不需要全停，", "逐步换料。");
        assertThat(rec.startInfo).isEqualTo("dashscope|qwen3.8-max|false");
        assertThat(rec.finishReason).isEqualTo("stop");
        assertThat(rec.fullText).isEqualTo("不需要全停，逐步换料。");
        assertThat(rec.result.getUsage()).isEqualTo(new AiUsage(20, 457, 477));
        assertThat(handle.isDone()).isTrue();

        JsonNode body = Jsons.parse(server.requests().getFirst().body());
        assertThat(body.get("stream").asBoolean()).isTrue();
        assertThat(body.get("stream_options").get("include_usage").asBoolean()).isTrue();
        assertThat(server.requests().getFirst().header("Accept")).contains("text/event-stream");
    }

    @Test
    void stream_primary503_fallsBackBeforeContent() throws Exception {
        route((r, ex) -> FakeHttpServer.json(ex, 503, "{}"), (r, ex) -> FakeHttpServer.sse(ex, QWEN_SSE, 0));
        Recorder rec = new Recorder();
        new AiGatewayImpl(props()).stream(textRequest(), rec);
        assertThat(rec.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.error).isNull();
        assertThat(rec.startInfo).isEqualTo("deepseek|qwen3.8-max|true");
        assertThat(rec.fullText).isEqualTo("不需要全停，逐步换料。");
        assertThat(rec.result.isFallback()).isTrue();
    }

    @Test
    void stream_emptyAnswer_fallsBack() throws Exception {
        List<String> empty = List.of("data: {\"choices\":[{\"delta\":{\"content\":\"\",\"reasoning_content\":\"想\"},"
                + "\"finish_reason\":\"length\"}]}\n\n", "data: [DONE]\n\n");
        route((r, ex) -> FakeHttpServer.sse(ex, empty, 0), (r, ex) -> FakeHttpServer.sse(ex, QWEN_SSE, 0));
        Recorder rec = new Recorder();
        new AiGatewayImpl(props()).stream(textRequest(), rec);
        assertThat(rec.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.error).isNull();
        assertThat(rec.result.getProvider()).isEqualTo("deepseek");
    }

    @Test
    void stream_interruptedAfterContent_noFallback() throws Exception {
        route((r, ex) -> FakeHttpServer.sse(ex, List.of(
                        "data: {\"choices\":[{\"delta\":{\"content\":\"已经说了一半\"}}]}\n\n",
                        "data: {\"choices\":[{\"delta\":{\"con\n\n"), 50),
                (r, ex) -> FakeHttpServer.sse(ex, QWEN_SSE, 0));
        Recorder rec = new Recorder();
        new AiGatewayImpl(props()).stream(textRequest(), rec);
        assertThat(rec.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.deltas).containsExactly("已经说了一半");
        assertThat(rec.error.getCode()).isEqualTo(50201);
        assertThat(server.requests("/f")).isEmpty();
    }

    @Test
    void stream_deadline_50401_afterPartialContent() throws Exception {
        route((r, ex) -> FakeHttpServer.sse(ex, List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"第一段\"}}]}\n\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"太晚了\"}}]}\n\n"), 3000),
                (r, ex) -> FakeHttpServer.sse(ex, QWEN_SSE, 0));
        Recorder rec = new Recorder();
        AiChatRequest req = AiChatRequest.builder().message(AiMessage.user("q")).timeout(Duration.ofMillis(800)).build();
        new AiGatewayImpl(props()).stream(req, rec);
        assertThat(rec.done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.deltas).containsExactly("第一段");
        assertThat(rec.error.getCode()).isEqualTo(50401);
    }

    @Test
    void stream_cancel_stopsCallbacksAndClosesUpstream() throws Exception {
        AtomicBoolean upstreamClosed = new AtomicBoolean();
        CountDownLatch closed = new CountDownLatch(1);
        route((r, ex) -> {
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            OutputStream os = ex.getResponseBody();
            try {
                for (int i = 0; i < 100; i++) {
                    os.write("data: {\"choices\":[{\"delta\":{\"content\":\"字\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                upstreamClosed.set(true);
                closed.countDown();
            }
        }, (r, ex) -> FakeHttpServer.json(ex, 500, "{}"));
        Recorder rec = new Recorder();
        AiStreamHandle handle = new AiGatewayImpl(props()).stream(textRequest(), rec);
        assertThat(rec.firstDelta.await(5, TimeUnit.SECONDS)).isTrue();
        handle.cancel();
        int seen = rec.deltas.size();

        assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(upstreamClosed).isTrue();
        Thread.sleep(300);
        assertThat(rec.deltas.size()).isLessThanOrEqualTo(seen + 1);
        assertThat(rec.done.getCount()).isEqualTo(1);
        assertThat(handle.isDone()).isTrue();
    }

    // ===== 语音识别 =====

    @Test
    void recognizeSpeech_inputAudioBody_andTrimmedText() {
        route((r, ex) -> FakeHttpServer.json(ex, 200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{"
                + "\"annotations\":[{\"emotion\":\"neutral\",\"language\":\"zh\",\"type\":\"audio_info\"}],"
                + "\"content\":\" 保育猪换料后拉稀，要停料吗？ \",\"role\":\"assistant\"}}],\"model\":\"qwen3-asr-flash\"}"),
                (r, ex) -> FakeHttpServer.json(ex, 500, "{}"));
        String text = new AiGatewayImpl(props()).recognizeSpeech(new byte[]{1, 2, 3}, "audio/wav");
        assertThat(text).isEqualTo("保育猪换料后拉稀，要停料吗？");

        JsonNode body = Jsons.parse(server.requests().getFirst().body());
        assertThat(body.get("model").asString()).isEqualTo("qwen3-asr-flash");
        JsonNode part = body.get("messages").get(0).get("content").get(0);
        assertThat(part.get("type").asString()).isEqualTo("input_audio");
        assertThat(part.get("input_audio").get("data").asString()).isEqualTo("data:audio/wav;base64,AQID");
        assertThat(body.get("asr_options").get("enable_itn").asBoolean()).isTrue();
    }

    @Test
    void recognizeSpeech_emptyAudio_40001() {
        assertThatThrownBy(() -> new AiGatewayImpl(props()).recognizeSpeech(new byte[0], "audio/wav"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    // ===== 工具 =====

    private static List<String> repeat(String chunk, int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(chunk);
        }
        return list;
    }

    static final class Recorder implements AiStreamHandler {
        final List<String> deltas = new CopyOnWriteArrayList<>();
        final CountDownLatch firstDelta = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        volatile String startInfo;
        volatile String finishReason;
        volatile String fullText;
        volatile AiChatResult result;
        volatile BizException error;

        @Override
        public void onStart(String provider, String model, boolean fallback) {
            startInfo = provider + "|" + model + "|" + fallback;
        }

        @Override
        public void onDelta(String text) {
            deltas.add(text);
            firstDelta.countDown();
        }

        @Override
        public void onResult(AiChatResult r) {
            result = r;
        }

        @Override
        public void onComplete(String finishReason, String fullText) {
            this.finishReason = finishReason;
            this.fullText = fullText;
            done.countDown();
        }

        @Override
        public void onError(BizException error) {
            this.error = error;
            done.countDown();
        }
    }
}
