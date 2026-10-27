package com.zhimu.server.integration.push;

import com.zhimu.server.integration.support.FakeHttpServer;
import com.zhimu.server.integration.support.Jsons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 极光推送：请求体（别名、intent、渠道、extras）、Basic 鉴权、结果分类与重试（本地假服务，不真实推送）。
 */
class JPushClientTest {

    private FakeHttpServer server;
    private PushProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = FakeHttpServer.start((r, ex) -> FakeHttpServer.json(ex, 200, "{\"sendno\":\"0\",\"msg_id\":\"18100287008546343\"}"));
        props = new PushProperties();
        props.setAppKey("test-app-key");
        props.setMasterSecret("test-master-secret");
        props.setBaseUrl(server.url());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private JPushClient client() {
        return new JPushClient(props, server.url(), new long[]{10, 30});
    }

    private static PushMessage message(String content) {
        return PushMessage.builder()
                .title("东山猪场 · 3 号育肥舍早餐投喂已逾期")
                .content(content)
                .channel(PushChannels.TODO)
                .extra("route", "T04").extra("farm_id", 1001L).extra("biz_type", "TODO")
                .extra("biz_id", 88L).extra("message_id", 501L)
                .build();
    }

    @Test
    void push_buildsAliasIntentChannelAndExtras() {
        PushResult r = client().pushToUsers(List.of(7L, 8L, 7L), message("请尽快完成投喂登记"));

        assertThat(r.status()).isEqualTo(PushStatus.SENT);
        assertThat(r.msgIds()).containsExactly("18100287008546343");
        FakeHttpServer.Recorded req = server.requests().getFirst();
        assertThat(req.path()).isEqualTo("/v3/push");
        String basic = Base64.getEncoder().encodeToString("test-app-key:test-master-secret".getBytes(StandardCharsets.UTF_8));
        assertThat(req.header("Authorization")).isEqualTo("Basic " + basic);

        JsonNode body = Jsons.parse(req.body());
        assertThat(body.get("platform").get(0).asString()).isEqualTo("android");
        assertThat(body.get("audience").get("alias").toString()).isEqualTo("[\"u7\",\"u8\"]");
        JsonNode android = body.get("notification").get("android");
        assertThat(android.get("title").asString()).isEqualTo("东山猪场 · 3 号育肥舍早餐投喂已逾期");
        assertThat(android.get("alert").asString()).isEqualTo("请尽快完成投喂登记");
        assertThat(android.get("channel_id").asString()).isEqualTo("zm_todo_v2");
        assertThat(android.get("display_foreground").asString()).isEqualTo("1");
        assertThat(android.get("intent").get("url").asString())
                .isEqualTo("intent:#Intent;action=com.zhimu.slmp.action.PUSH_OPEN;end");
        JsonNode extras = android.get("extras");
        assertThat(extras.get("route").asString()).isEqualTo("T04");
        assertThat(extras.get("farm_id").asLong()).isEqualTo(1001L);
        assertThat(extras.get("message_id").asLong()).isEqualTo(501L);
        assertThat(extras.get("channel").asString()).isEqualTo("zm_todo_v2");
        assertThat(body.get("options").get("time_to_live").asLong()).isEqualTo(86400);
        assertThat(body.get("options").get("apns_production").asBoolean()).isFalse();
    }

    @Test
    void content_limitedTo60Chars() {
        String longText = "很".repeat(80);
        assertThat(JPushClient.limitContent(longText)).hasSize(60).endsWith("…");
        assertThat(JPushClient.limitContent("短")).isEqualTo("短");
    }

    @Test
    void notConfigured_skipsWithoutRequest() {
        props.setMasterSecret("");
        JPushClient c = client();
        assertThat(c.isEnabled()).isFalse();
        assertThat(c.pushToUsers(List.of(1L), message("x")).status()).isEqualTo(PushStatus.SKIPPED);
        assertThat(c.bindAlias("1a0018970a8b2c3d", "u1").status()).isEqualTo(PushStatus.SKIPPED);
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void noTarget1011_skipped() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 400,
                "{\"error\":{\"code\":1011,\"message\":\"cannot find user by this audience\"},\"msg_id\":\"1\"}"));
        PushResult r = client().pushToUsers(List.of(1L), message("x"));
        assertThat(r.status()).isEqualTo(PushStatus.SKIPPED);
        assertThat(r.errorCode()).isEqualTo(1011);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void serverError_retriedTwiceThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        server.handler((r, ex) -> {
            if (calls.incrementAndGet() < 3) {
                FakeHttpServer.json(ex, 503, "{\"error\":{\"code\":1030,\"message\":\"timeout\"}}");
            } else {
                FakeHttpServer.json(ex, 200, "{\"sendno\":\"0\",\"msg_id\":\"m3\"}");
            }
        });
        PushResult r = client().pushToUsers(List.of(1L), message("x"));
        assertThat(r.status()).isEqualTo(PushStatus.SENT);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void serverError_failsAfterTwoRetries_noException() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 500, "{\"error\":{\"code\":1000,\"message\":\"x\"}}"));
        PushResult r = client().pushToUsers(List.of(1L), message("x"));
        assertThat(r.status()).isEqualTo(PushStatus.FAILED);
        assertThat(server.requests()).hasSize(3);
    }

    @Test
    void authErrorAndRateLimit_notRetried() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 401, "{\"error\":{\"code\":1004,\"message\":\"Basic authentication failed\"}}"));
        assertThat(client().pushToUsers(List.of(1L), message("x")).status()).isEqualTo(PushStatus.FAILED);
        assertThat(server.requests()).hasSize(1);

        server.handler((r, ex) -> FakeHttpServer.json(ex, 429, "{\"error\":{\"code\":2002,\"message\":\"rate limit\"}}"));
        assertThat(client().pushToUsers(List.of(1L), message("x")).status()).isEqualTo(PushStatus.RATE_LIMITED);
        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void networkError_failsWithoutException() {
        props.setBaseUrl("http://127.0.0.1:1");
        PushResult r = client().pushToUsers(List.of(1L), message("x"));
        assertThat(r.status()).isEqualTo(PushStatus.FAILED);
    }

    @Test
    void moreThan1000Aliases_splitIntoBatches() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 1500; i++) {
            ids.add(i);
        }
        PushResult r = client().pushToUsers(ids, message("x"));
        assertThat(r.status()).isEqualTo(PushStatus.SENT);
        assertThat(server.requests()).hasSize(2);
        assertThat(Jsons.parse(server.requests().get(0).body()).get("audience").get("alias").size()).isEqualTo(1000);
        assertThat(Jsons.parse(server.requests().get(1).body()).get("audience").get("alias").size()).isEqualTo(500);
        assertThat(r.msgIds()).hasSize(2);
    }

    @Test
    void bindAndUnbindAlias_deviceApi() {
        server.handler((r, ex) -> FakeHttpServer.respond(ex, 200, "application/json", ""));
        JPushClient c = client();
        assertThat(c.bindAlias("1a0018970a8b2c3d", PushClient.alias(42)).status()).isEqualTo(PushStatus.SENT);
        assertThat(c.unbindAlias("1a0018970a8b2c3d").status()).isEqualTo(PushStatus.SENT);
        List<FakeHttpServer.Recorded> reqs = server.requests();
        assertThat(reqs.get(0).path()).isEqualTo("/v3/devices/1a0018970a8b2c3d");
        assertThat(Jsons.parse(reqs.get(0).body()).get("alias").asString()).isEqualTo("u42");
        assertThat(Jsons.parse(reqs.get(1).body()).get("alias").asString()).isEmpty();
        assertThat(c.bindAlias("../bad", "u1").status()).isEqualTo(PushStatus.FAILED);
    }
}
