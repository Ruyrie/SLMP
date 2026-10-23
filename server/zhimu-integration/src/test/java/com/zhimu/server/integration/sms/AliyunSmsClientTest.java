package com.zhimu.server.integration.sms;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阿里云号码认证：请求参数与签名头、响应解析与错误映射（本地假服务，不真实发短信）。
 */
class AliyunSmsClientTest {

    private static final long T0 = 1698315752000L; // 2023-10-26T10:22:32Z
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    private FakeHttpServer server;
    private final AtomicLong now = new AtomicLong(T0);
    private volatile int status = 200;
    private volatile String body = "";
    private AliyunSmsClient client;
    private SmsProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = FakeHttpServer.start((r, ex) -> FakeHttpServer.json(ex, status, body));
        props = new SmsProperties();
        props.setMode(SmsMode.ALIYUN);
        props.setAccessKeyId("testKeyId");
        props.setAccessKeySecret("testSecret");
        props.setSignName("恒创联众");
        props.setTemplates(Map.of("LOGIN_REGISTER", "100001", "RESET_PASSWORD", "100003"));
        client = new AliyunSmsClient(props, server.url(), now::get, () -> NONCE);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private void respond(int status, String body) {
        this.status = status;
        this.body = body;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> q = new TreeMap<>();
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            q.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    @Test
    void send_success_buildsSignedRequest() {
        respond(200, "{\"Code\":\"OK\",\"Message\":\"成功\",\"Success\":true,\"RequestId\":\"REQ-S\","
                + "\"Model\":{\"BizId\":\"BIZ-1\",\"RequestId\":\"sub\",\"OutId\":\"\"}}");
        SmsSendResult r = client.send("13800006620", SmsTemplate.LOGIN_REGISTER);

        assertThat(r).isEqualTo(new SmsSendResult("BIZ-1", "REQ-S", 60, 300));
        FakeHttpServer.Recorded req = server.requests().getFirst();
        assertThat(req.method()).isEqualTo("POST");
        Map<String, String> q = query(req.rawQuery());
        assertThat(q).containsEntry("PhoneNumber", "13800006620")
                .containsEntry("SignName", "恒创联众")
                .containsEntry("TemplateCode", "100001")
                .containsEntry("TemplateParam", "{\"code\":\"##code##\",\"min\":\"5\"}")
                .containsEntry("CodeLength", "6")
                .containsEntry("CodeType", "1")
                .containsEntry("ValidTime", "300")
                .containsEntry("Interval", "60")
                .containsEntry("DuplicatePolicy", "1")
                .containsEntry("ReturnVerifyCode", "false");
        assertThat(req.header("x-acs-action")).isEqualTo("SendSmsVerifyCode");
        assertThat(req.header("x-acs-version")).isEqualTo("2017-05-25");
        assertThat(req.header("x-acs-date")).isEqualTo("2023-10-26T10:22:32Z");
        assertThat(req.header("x-acs-signature-nonce")).isEqualTo(NONCE);

        // 查询串与签名头和按同样输入独立计算的结果一致（签名用的 host 为配置的 endpoint）
        AcsV3Signer.Signed expected = AcsV3Signer.sign("testKeyId", "testSecret", "POST", "dypnsapi.aliyuncs.com",
                "SendSmsVerifyCode", "2017-05-25", q, "2023-10-26T10:22:32Z", NONCE);
        assertThat(req.rawQuery()).isEqualTo(expected.canonicalQuery());
        assertThat(req.header("Authorization")).isEqualTo(expected.authorization())
                .startsWith("ACS3-HMAC-SHA256 Credential=testKeyId,SignedHeaders=");
    }

    @Test
    void send_frequency_maps42901WithRemainingCooldown() {
        respond(200, "{\"Code\":\"OK\",\"RequestId\":\"REQ-1\",\"Model\":{\"BizId\":\"B\"}}");
        client.send("13800006620", SmsTemplate.LOGIN_REGISTER);

        now.addAndGet(20_000);
        respond(400, "{\"Code\":\"biz.FREQUENCY\",\"Message\":\"Check frequency fail.\",\"RequestId\":\"REQ-2\"}");
        assertThatThrownBy(() -> client.send("13800006620", SmsTemplate.LOGIN_REGISTER))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(42901);
                    assertThat(e.getMessage()).isEqualTo("操作太频繁，请稍后再试");
                    assertThat(((Map<?, ?>) e.getData()).get("cooldown_seconds")).isEqualTo(40);
                });
    }

    @Test
    void send_frequencyWithoutLocalRecord_usesFullInterval() {
        for (String code : new String[]{"FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL", "Throttling.User"}) {
            respond(400, "{\"Code\":\"" + code + "\",\"RequestId\":\"R\"}");
            assertThatThrownBy(() -> client.send("13900001234", SmsTemplate.LOGIN_REGISTER))
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(42901);
                        assertThat(((Map<?, ?>) e.getData()).get("cooldown_seconds")).isEqualTo(60);
                    });
        }
    }

    @Test
    void send_signatureError_maps50301() {
        respond(400, "{\"RequestId\":\"REQ-SIG\",\"Code\":\"SignatureDoesNotMatch\","
                + "\"Message\":\"Specified signature is not matched with our calculation.\"}");
        assertThatThrownBy(() -> client.send("13800006620", SmsTemplate.LOGIN_REGISTER))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
    }

    @Test
    void send_nonJsonAndPhoneInvalid() {
        respond(502, "<html>Bad Gateway</html>");
        assertThatThrownBy(() -> client.send("13800006620", SmsTemplate.LOGIN_REGISTER))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));

        respond(400, "{\"Code\":\"isv.MOBILE_NUMBER_ILLEGAL\",\"RequestId\":\"R\"}");
        assertThatThrownBy(() -> client.send("1380000", SmsTemplate.LOGIN_REGISTER))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void notConfigured_failsWithoutRequest() {
        props.setAccessKeyId("");
        assertThatThrownBy(() -> client.send("13800006620", SmsTemplate.LOGIN_REGISTER))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
        assertThatThrownBy(() -> client.send("13800006620", SmsTemplate.BIND_NEW_PHONE))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void verify_passUnknownAndValidateFail() {
        respond(200, "{\"Code\":\"OK\",\"Success\":true,\"RequestId\":\"REQ-C\",\"Model\":{\"VerifyResult\":\"PASS\"}}");
        assertThat(client.verify("13800006620", "482913")).isTrue();
        FakeHttpServer.Recorded req = server.requests().getFirst();
        assertThat(req.header("x-acs-action")).isEqualTo("CheckSmsVerifyCode");
        assertThat(query(req.rawQuery())).containsEntry("PhoneNumber", "13800006620")
                .containsEntry("VerifyCode", "482913");

        respond(200, "{\"Code\":\"OK\",\"RequestId\":\"REQ-U\",\"Model\":{\"VerifyResult\":\"UNKNOWN\"}}");
        assertThat(client.verify("13800006620", "000000")).isFalse();

        respond(400, "{\"Code\":\"isv.ValidateFail\",\"Message\":\"fail\",\"RequestId\":\"REQ-V\"}");
        assertThat(client.verify("13800006620", "000000")).isFalse();

        assertThat(client.verify("13800006620", " ")).isFalse();
    }

    @Test
    void verify_otherError_50301() {
        respond(403, "{\"Code\":\"InvalidAccessKeyId.NotFound\",\"RequestId\":\"R\"}");
        assertThatThrownBy(() -> client.verify("13800006620", "123456"))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
    }
}
