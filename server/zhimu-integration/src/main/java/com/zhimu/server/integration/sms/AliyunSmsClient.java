package com.zhimu.server.integration.sms;

import com.zhimu.server.common.util.Masks;
import com.zhimu.server.integration.support.HttpClients;
import com.zhimu.server.integration.support.Jsons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 阿里云号码认证服务（Dypnsapi 2017-05-25）：SendSmsVerifyCode 与 CheckSmsVerifyCode，V3 签名，参数与 App 的 AliyunSmsClient 一致。
 * 验证码由阿里云生成并校验。日志只记接口名、脱敏手机号、返回码与 RequestId，不记 Key 与验证码。
 */
@Slf4j
public class AliyunSmsClient implements SmsClient {

    public static final String API_VERSION = "2017-05-25";
    public static final String ACTION_SEND = "SendSmsVerifyCode";
    public static final String ACTION_CHECK = "CheckSmsVerifyCode";

    static final String CODE_OK = "OK";
    static final String VERIFY_PASS = "PASS";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_TRACKED_PHONES = 10_000;

    private final SmsProperties props;
    private final String baseUrl;
    private final LongSupplier clock;
    private final Supplier<String> nonce;
    private final RestClient restClient;
    /** 手机号 → 上次发送成功的时间，用于频率限制时给出剩余秒数（只在内存） */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public AliyunSmsClient(SmsProperties props) {
        this(props, "https://" + props.getEndpoint(), System::currentTimeMillis, AcsV3Signer::newNonce);
    }

    AliyunSmsClient(SmsProperties props, String baseUrl, LongSupplier clock, Supplier<String> nonce) {
        this.props = props;
        this.baseUrl = baseUrl;
        this.clock = clock;
        this.nonce = nonce;
        this.restClient = HttpClients.restClient(TIMEOUT, TIMEOUT);
        if (!props.aliyunConfigured()) {
            log.warn("短信模式为 aliyun，但未配置 AccessKey 或签名，发送将返回 50301");
        }
    }

    @Override
    public SmsMode mode() {
        return SmsMode.ALIYUN;
    }

    /** 由阿里云生成验证码，min 为有效分钟数 */
    String templateParam() {
        return "{\"code\":\"##code##\",\"min\":\"" + (props.getValidSeconds() / 60) + "\"}";
    }

    @Override
    public SmsSendResult send(String phone, SmsTemplate template) {
        String templateCode = props.templateCode(template);
        if (templateCode == null || templateCode.isBlank()) {
            log.error("短信模板未配置 template={}", template);
            throw AliyunSmsErrors.failed();
        }
        Map<String, String> q = new TreeMap<>();
        q.put("PhoneNumber", phone);
        q.put("SignName", props.getSignName());
        q.put("TemplateCode", templateCode);
        q.put("TemplateParam", templateParam());
        q.put("CodeLength", String.valueOf(props.getCodeLength()));
        q.put("CodeType", "1");
        q.put("ValidTime", String.valueOf(props.getValidSeconds()));
        q.put("Interval", String.valueOf(props.getIntervalSeconds()));
        q.put("DuplicatePolicy", "1");
        q.put("ReturnVerifyCode", "false");
        JsonNode resp = call(ACTION_SEND, phone, q, cooldownForSend(phone));
        rememberSent(phone);
        JsonNode model = resp.get("Model");
        return new SmsSendResult(Jsons.text(model, "BizId"), Jsons.text(resp, "RequestId"),
                props.getIntervalSeconds(), props.getValidSeconds());
    }

    @Override
    public boolean verify(String phone, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Map<String, String> q = new TreeMap<>();
        q.put("PhoneNumber", phone);
        q.put("VerifyCode", code);
        JsonNode resp = call(ACTION_CHECK, phone, q, props.getIntervalSeconds());
        if (resp == null) {
            return false;
        }
        return VERIFY_PASS.equals(Jsons.text(resp.get("Model"), "VerifyResult"));
    }

    /** 发送被频率限制时的倒计时秒数：本机记录的剩余间隔，没有记录（如重启过）时按完整间隔 */
    int cooldownForSend(String phone) {
        int interval = props.getIntervalSeconds();
        Long last = lastSentAt.get(phone);
        if (last != null) {
            long left = interval - (clock.getAsLong() - last) / 1000L;
            if (left > 0) {
                return (int) left;
            }
        }
        return interval;
    }

    private void rememberSent(String phone) {
        long now = clock.getAsLong();
        if (lastSentAt.size() >= MAX_TRACKED_PHONES) {
            long expire = now - props.getIntervalSeconds() * 1000L;
            lastSentAt.values().removeIf(t -> t < expire);
        }
        lastSentAt.put(phone, now);
    }

    /**
     * 签名并执行一次请求：返回码 OK 时返回响应 JSON；CheckSmsVerifyCode 的 isv.ValidateFail 返回 null；其余抛出 BizException。
     */
    private JsonNode call(String action, String phone, Map<String, String> query, int cooldownSeconds) {
        if (!props.aliyunConfigured()) {
            log.warn("短信调用跳过 action={} phone={}：未配置 AccessKey 或签名", action, Masks.phone(phone));
            throw AliyunSmsErrors.failed();
        }
        AcsV3Signer.Signed signed = AcsV3Signer.sign(props.getAccessKeyId(), props.getAccessKeySecret(), "POST",
                props.getEndpoint(), action, API_VERSION, query, AcsV3Signer.formatDate(clock.getAsLong()),
                nonce.get());
        long t0 = System.nanoTime();
        ResponseEntity<String> resp;
        try {
            resp = restClient.post()
                    .uri(URI.create(baseUrl + "/?" + signed.canonicalQuery()))
                    .headers(h -> signed.headers().forEach((name, value) -> {
                        if (!"host".equals(name)) {
                            h.set(name, value);
                        }
                    }))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                    })
                    .toEntity(String.class);
        } catch (RestClientException e) {
            log.warn("短信调用失败 action={} phone={} costMs={}：网络错误 {}", action, Masks.phone(phone), costMs(t0),
                    e.getClass().getSimpleName());
            throw AliyunSmsErrors.failed();
        }
        int http = resp.getStatusCode().value();
        JsonNode json = Jsons.parse(resp.getBody());
        if (json == null || !json.isObject()) {
            log.warn("短信调用失败 action={} phone={} http={} costMs={}：响应无法解析", action, Masks.phone(phone), http,
                    costMs(t0));
            throw AliyunSmsErrors.failed();
        }
        String code = Jsons.text(json, "Code");
        String requestId = Jsons.text(json, "RequestId");
        if (CODE_OK.equals(code)) {
            log.info("短信调用成功 action={} phone={} requestId={} costMs={}", action, Masks.phone(phone), requestId,
                    costMs(t0));
            return json;
        }
        log.warn("短信调用失败 action={} phone={} http={} code={} message={} requestId={} costMs={}", action,
                Masks.phone(phone), http, code, Jsons.abbreviate(Jsons.text(json, "Message"), 200), requestId,
                costMs(t0));
        if (ACTION_CHECK.equals(action) && AliyunSmsErrors.isVerifyFail(code)) {
            return null;
        }
        throw AliyunSmsErrors.toBizException(code, cooldownSeconds);
    }

    private static long costMs(long t0) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    }
}
