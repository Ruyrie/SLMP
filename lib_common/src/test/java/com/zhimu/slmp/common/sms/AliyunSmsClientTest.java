package com.zhimu.slmp.common.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * AliyunSmsClient：请求参数与签名头、响应解析与错误映射（用拦截器返回固定响应，不访问网络）。
 */
public class AliyunSmsClientTest {

    private static final String KEY_ID = "testKeyId";
    private static final String SECRET = "testSecret";
    private static final String SIGN = "恒创联众";
    private static final long T0 = 1698315752000L; // 2023-10-26T10:22:32Z

    private final List<Request> requests = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(T0);
    private int nextStatus = 200;
    private String nextBody = "";
    private boolean failIo;
    private AliyunSmsClient client;

    @Before
    public void setUp() {
        Interceptor fake = chain -> {
            requests.add(chain.request());
            if (failIo) {
                throw new IOException("offline");
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(nextStatus)
                    .message("x")
                    .body(ResponseBody.create(nextBody, MediaType.get("application/json")))
                    .build();
        };
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(fake).build();
        client = new AliyunSmsClient(new AliyunSmsConfig(true, KEY_ID, SECRET, SIGN), http, now::get,
                () -> "0123456789abcdef0123456789abcdef");
    }

    private void respond(int status, String body) {
        nextStatus = status;
        nextBody = body;
    }

    @Test
    public void config_enabledOnlyInDebugWithAllValues() {
        assertTrue(new AliyunSmsConfig(true, "a", "b", "c").isEnabled());
        assertFalse(new AliyunSmsConfig(false, "a", "b", "c").isEnabled());
        assertFalse(new AliyunSmsConfig(true, "", "b", "c").isEnabled());
        assertFalse(new AliyunSmsConfig(true, "a", null, "c").isEnabled());
        assertFalse(new AliyunSmsConfig(true, "a", "b", " ").isEnabled());
        String text = new AliyunSmsConfig(true, "KEYID-X", "SECRET-X", "SIGN-X").toString();
        assertFalse(text.contains("KEYID-X") || text.contains("SECRET-X"));
    }

    @Test
    public void send_success_buildsSignedRequest() {
        respond(200, "{\"Code\":\"OK\",\"Message\":\"成功\",\"Success\":true,\"RequestId\":\"REQ-S\","
                + "\"Model\":{\"BizId\":\"BIZ-1\",\"RequestId\":\"sub\",\"OutId\":\"\"}}");
        SmsSendResult r = client.sendVerifyCode("13800006620", AliyunSmsTemplates.LOGIN_REGISTER).blockingGet();

        assertEquals("BIZ-1", r.getBizId());
        assertEquals("REQ-S", r.getRequestId());
        assertEquals(60, r.getCooldownSeconds());
        assertEquals(300, r.getExpireSeconds());

        Request req = requests.get(0);
        assertEquals("POST", req.method());
        assertEquals("dypnsapi.aliyuncs.com", req.url().host());
        assertEquals("13800006620", req.url().queryParameter("PhoneNumber"));
        assertEquals(SIGN, req.url().queryParameter("SignName"));
        assertEquals("100001", req.url().queryParameter("TemplateCode"));
        assertEquals("{\"code\":\"##code##\",\"min\":\"5\"}", req.url().queryParameter("TemplateParam"));
        assertEquals("6", req.url().queryParameter("CodeLength"));
        assertEquals("300", req.url().queryParameter("ValidTime"));
        assertEquals("60", req.url().queryParameter("Interval"));
        assertEquals("SendSmsVerifyCode", req.header("x-acs-action"));
        assertEquals("2017-05-25", req.header("x-acs-version"));
        assertEquals("2023-10-26T10:22:32Z", req.header("x-acs-date"));
        assertEquals("0123456789abcdef0123456789abcdef", req.header("x-acs-signature-nonce"));

        // 签名头与按同样输入独立计算的结果一致
        Map<String, String> q = new TreeMap<>();
        for (String name : req.url().queryParameterNames()) {
            q.put(name, req.url().queryParameter(name));
        }
        AcsV3Signer.Signed expected = AcsV3Signer.sign(KEY_ID, SECRET, "POST", "dypnsapi.aliyuncs.com",
                "SendSmsVerifyCode", "2017-05-25", q, "2023-10-26T10:22:32Z", "0123456789abcdef0123456789abcdef");
        assertEquals(expected.authorization, req.header("Authorization"));
        assertTrue(req.header("Authorization").startsWith("ACS3-HMAC-SHA256 Credential=testKeyId,SignedHeaders="));
        assertEquals(expected.canonicalQuery, req.url().encodedQuery());
    }

    @Test
    public void send_frequency_maps42901WithRemainingCooldown() {
        respond(200, "{\"Code\":\"OK\",\"RequestId\":\"REQ-1\",\"Model\":{\"BizId\":\"B\"}}");
        client.sendVerifyCode("13800006620", AliyunSmsTemplates.LOGIN_REGISTER).blockingGet();

        now.addAndGet(20_000);
        respond(400, "{\"Code\":\"biz.FREQUENCY\",\"Message\":\"Check frequency fail.\",\"RequestId\":\"REQ-2\"}");
        ApiException e = sendError("13800006620");
        assertEquals(ErrorCodes.TOO_FREQUENT, e.getCode());
        assertEquals("操作太频繁，请稍后再试", e.getMessage());
        assertEquals("40", e.dataString("cooldown_seconds"));
        assertEquals("REQ-2", e.getTraceId());
    }

    @Test
    public void send_frequency_withoutLocalRecord_usesFullInterval() {
        respond(400, "{\"Code\":\"FREQUENCY_FAIL\",\"Message\":\"Check frequency fail.\",\"RequestId\":\"R\"}");
        ApiException e = sendError("13900001234");
        assertEquals(ErrorCodes.TOO_FREQUENT, e.getCode());
        assertEquals("60", e.dataString("cooldown_seconds"));
    }

    @Test
    public void send_signatureError_maps50301WithTrace() {
        respond(400, "{\"RequestId\":\"REQ-SIG\",\"HostId\":\"dypnsapi.aliyuncs.com\",\"Code\":\"SignatureDoesNotMatch\","
                + "\"Message\":\"Specified signature is not matched with our calculation.\"}");
        ApiException e = sendError("13800006620");
        assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        assertTrue(e.getMessage().contains("SignatureDoesNotMatch"));
        assertTrue(e.getMessage().contains("Specified signature is not matched"));
        assertEquals("REQ-SIG", e.getTraceId());
    }

    @Test
    public void send_nonJson_maps50301() {
        respond(502, "<html>Bad Gateway</html>");
        ApiException e = sendError("13800006620");
        assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        assertTrue(e.getMessage().contains("502"));
    }

    @Test
    public void send_ioFailure_mapsNetwork() {
        failIo = true;
        ApiException e = sendError("13800006620");
        assertEquals(ErrorCodes.NETWORK, e.getCode());
    }

    @Test
    public void disabled_failsWithoutRequest() {
        AliyunSmsClient off = new AliyunSmsClient(new AliyunSmsConfig(true, "", "", ""),
                new OkHttpClient.Builder().addInterceptor(chain -> {
                    fail("should not call");
                    return null;
                }).build(), now::get, () -> "n");
        assertFalse(off.isEnabled());
        try {
            off.sendVerifyCode("13800006620", AliyunSmsTemplates.LOGIN_REGISTER).blockingGet();
            fail();
        } catch (ApiException e) {
            assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    @Test
    public void check_pass_completes() {
        respond(200, "{\"Code\":\"OK\",\"Success\":true,\"RequestId\":\"REQ-C\",\"Model\":{\"VerifyResult\":\"PASS\"}}");
        client.checkVerifyCode("13800006620", "482913").blockingAwait();
        Request req = requests.get(0);
        assertEquals("CheckSmsVerifyCode", req.header("x-acs-action"));
        assertEquals("13800006620", req.url().queryParameter("PhoneNumber"));
        assertEquals("482913", req.url().queryParameter("VerifyCode"));
    }

    @Test
    public void check_unknown_maps40103() {
        respond(200, "{\"Code\":\"OK\",\"Success\":true,\"RequestId\":\"REQ-U\",\"Model\":{\"VerifyResult\":\"UNKNOWN\"}}");
        ApiException e = checkError();
        assertEquals(ErrorCodes.SMS_CODE_WRONG, e.getCode());
        assertEquals("验证码不正确或已过期，请重新获取", e.getMessage());
        assertEquals("REQ-U", e.getTraceId());
    }

    @Test
    public void check_validateFail_maps40103() {
        respond(400, "{\"Code\":\"isv.ValidateFail\",\"Message\":\"fail\",\"RequestId\":\"REQ-V\"}");
        ApiException e = checkError();
        assertEquals(ErrorCodes.SMS_CODE_WRONG, e.getCode());
        assertNull(e.dataString("cooldown_seconds"));
    }

    private ApiException sendError(String phone) {
        try {
            client.sendVerifyCode(phone, AliyunSmsTemplates.LOGIN_REGISTER).blockingGet();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected ApiException");
    }

    private ApiException checkError() {
        try {
            client.checkVerifyCode("13800006620", "000000").blockingAwait();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected ApiException");
    }
}
