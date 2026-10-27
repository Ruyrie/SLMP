package com.zhimu.server.integration.push;

import com.zhimu.server.common.util.Masks;
import com.zhimu.server.integration.support.HttpClients;
import com.zhimu.server.integration.support.Jsons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 极光推送 REST API v3（模块03 服务端 2.8.4）：HTTP Basic（AppKey:Master Secret），超时 5 秒；
 * 超时、网络错误与 5xx 间隔 1 秒、3 秒重试 2 次；1011（别名下无设备）为 SKIPPED；频率超限为 RATE_LIMITED。
 * <p>
 * 点击通知：notification.android.intent 为 {@code intent:#Intent;action={intent-action};end}，打开 App 的 PushRouterActivity。
 * 厂商通道下发策略用极光默认值（免费账号为 secondary_push：极光在线走极光，离线走厂商通道）。
 */
@Slf4j
public class JPushClient implements PushClient {

    static final String DEVICE_BASE_URL = "https://device.jpush.cn";
    static final int MAX_ALIASES_PER_PUSH = 1000;
    static final int MAX_CONTENT_LENGTH = 60;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long TIME_TO_LIVE_SECONDS = 86_400;
    private static final Pattern REGISTRATION_ID = Pattern.compile("[A-Za-z0-9]{1,64}");

    private final PushProperties props;
    private final String deviceBaseUrl;
    private final long[] retryDelaysMs;
    private final RestClient restClient;

    public JPushClient(PushProperties props) {
        this(props, DEVICE_BASE_URL, new long[]{1000, 3000});
    }

    JPushClient(PushProperties props, String deviceBaseUrl, long[] retryDelaysMs) {
        this.props = props;
        this.deviceBaseUrl = deviceBaseUrl;
        this.retryDelaysMs = retryDelaysMs.clone();
        this.restClient = HttpClients.restClient(TIMEOUT, TIMEOUT);
        if (props.configured()) {
            log.info("极光推送已配置 appKey={}", Masks.secret(props.getAppKey()));
        } else {
            log.info("极光推送未配置 Master Secret：推送与别名设置只写日志");
        }
    }

    @Override
    public boolean isEnabled() {
        return props.configured();
    }

    @Override
    public PushResult pushToUsers(Collection<Long> userIds, PushMessage message) {
        List<String> aliases = userIds == null ? List.of() : userIds.stream().filter(Objects::nonNull).distinct()
                .map(PushClient::alias).toList();
        String ctx = "users=" + aliases.size() + " channel=" + message.getChannel()
                + " biz_type=" + message.getExtras().get("biz_type")
                + " message_id=" + message.getExtras().get("message_id");
        if (aliases.isEmpty()) {
            return PushResult.skipped("没有接收人");
        }
        if (!props.configured()) {
            log.info("极光推送跳过（未配置 Master Secret） {}", ctx);
            return PushResult.skipped("未配置极光 Master Secret");
        }
        List<PushResult> results = new ArrayList<>();
        for (int from = 0; from < aliases.size(); from += MAX_ALIASES_PER_PUSH) {
            List<String> batch = aliases.subList(from, Math.min(aliases.size(), from + MAX_ALIASES_PER_PUSH));
            String body = Jsons.write(buildPushBody(batch, message));
            results.add(execute("push", URI.create(trimSlash(props.getBaseUrl()) + "/v3/push"), body, ctx));
        }
        return merge(results);
    }

    @Override
    public PushResult bindAlias(String registrationId, String alias) {
        return setAlias(registrationId, alias == null ? "" : alias);
    }

    @Override
    public PushResult unbindAlias(String registrationId) {
        return setAlias(registrationId, "");
    }

    private PushResult setAlias(String registrationId, String alias) {
        String op = alias.isEmpty() ? "unbindAlias" : "bindAlias";
        String ctx = "registration_id=" + Masks.secret(registrationId) + " alias=" + alias;
        if (registrationId == null || !REGISTRATION_ID.matcher(registrationId).matches()) {
            log.warn("极光{}跳过：registration_id 格式不正确 {}", op, ctx);
            return new PushResult(PushStatus.FAILED, List.of(), null, "registration_id 格式不正确");
        }
        if (!props.configured()) {
            log.info("极光{}跳过（未配置 Master Secret） {}", op, ctx);
            return PushResult.skipped("未配置极光 Master Secret");
        }
        ObjectNode body = Jsons.object().put("alias", alias);
        URI uri = URI.create(trimSlash(deviceBaseUrl) + "/v3/devices/" + registrationId);
        return execute(op, uri, Jsons.write(body), ctx);
    }

    /** 推送请求体 */
    ObjectNode buildPushBody(List<String> aliases, PushMessage m) {
        ObjectNode root = Jsons.object();
        root.putArray("platform").add("android");
        var aliasArray = root.putObject("audience").putArray("alias");
        aliases.forEach(aliasArray::add);

        Map<String, Object> extras = new LinkedHashMap<>(m.getExtras());
        if (m.getChannel() != null) {
            extras.putIfAbsent("channel", m.getChannel());
        }
        ObjectNode android = root.putObject("notification").putObject("android");
        android.put("alert", limitContent(m.getContent()));
        if (m.getTitle() != null) {
            android.put("title", m.getTitle());
        }
        if (m.getChannel() != null) {
            android.put("channel_id", m.getChannel());
        }
        // 极光默认可隐藏前台通知，显式要求 App 在前台时也展示。
        android.put("display_foreground", "1");
        android.putObject("intent").put("url", "intent:#Intent;action=" + props.getIntentAction() + ";end");
        android.set("extras", Jsons.MAPPER.valueToTree(extras));

        ObjectNode options = root.putObject("options");
        options.put("time_to_live", TIME_TO_LIVE_SECONDS);
        options.put("apns_production", props.isApnsProduction());
        return root;
    }

    static String limitContent(String content) {
        if (content == null) {
            return "";
        }
        if (content.codePointCount(0, content.length()) <= MAX_CONTENT_LENGTH) {
            return content;
        }
        int end = content.offsetByCodePoints(0, MAX_CONTENT_LENGTH - 1);
        return content.substring(0, end) + "…";
    }

    private PushResult execute(String op, URI uri, String body, String ctx) {
        String auth = "Basic " + Base64.getEncoder().encodeToString(
                (props.getAppKey() + ":" + props.getMasterSecret()).getBytes(StandardCharsets.UTF_8));
        long t0 = System.nanoTime();
        PushResult last = null;
        for (int attempt = 0; attempt <= retryDelaysMs.length; attempt++) {
            if (attempt > 0 && !sleep(retryDelaysMs[attempt - 1])) {
                break;
            }
            Outcome outcome = callOnce(uri, auth, body);
            last = outcome.result;
            if (!outcome.retryable) {
                break;
            }
            if (attempt < retryDelaysMs.length) {
                log.info("极光{}失败，准备重试 {} attempt={} http={} 原因={}", op, ctx, attempt + 1, outcome.http,
                        last.reason());
            }
        }
        long cost = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        if (last.status() == PushStatus.FAILED) {
            log.warn("极光{}失败 {} errorCode={} 原因={} costMs={}", op, ctx, last.errorCode(), last.reason(), cost);
        } else {
            log.info("极光{} {} status={} msgId={} errorCode={} costMs={}", op, ctx, last.status(), last.msgIds(),
                    last.errorCode(), cost);
        }
        return last;
    }

    private record Outcome(PushResult result, boolean retryable, int http) {
    }

    private Outcome callOnce(URI uri, String auth, String body) {
        ResponseEntity<String> resp;
        try {
            resp = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                    })
                    .toEntity(String.class);
        } catch (RestClientException e) {
            String reason = HttpClients.isTimeout(e) ? "超时" : "网络错误：" + e.getClass().getSimpleName();
            return new Outcome(new PushResult(PushStatus.FAILED, List.of(), null, reason), true, -1);
        }
        int http = resp.getStatusCode().value();
        JsonNode json = Jsons.parse(resp.getBody());
        if (resp.getStatusCode().is2xxSuccessful()) {
            return new Outcome(PushResult.sent(Jsons.text(json, "msg_id")), false, http);
        }
        JsonNode error = json == null ? null : json.get("error");
        Integer code = Jsons.integer(error, "code");
        String message = Jsons.abbreviate(Jsons.text(error, "message"), 200);
        String reason = "HTTP " + http + (message == null ? "" : " " + message);
        if (code != null && code == 1011) {
            return new Outcome(new PushResult(PushStatus.SKIPPED, List.of(), code, "没有满足条件的推送目标"), false, http);
        }
        if (http == 429 || (code != null && code == 2002)) {
            return new Outcome(new PushResult(PushStatus.RATE_LIMITED, List.of(), code, reason), false, http);
        }
        if (http == 401 || (code != null && code == 1004)) {
            log.error("极光鉴权失败，请检查 AppKey 与 Master Secret：{}", reason);
        }
        boolean retryable = http >= 500 || (code != null && (code == 1000 || code == 1030));
        return new Outcome(new PushResult(PushStatus.FAILED, List.of(), code, reason), retryable, http);
    }

    private static PushResult merge(List<PushResult> results) {
        if (results.size() == 1) {
            return results.getFirst();
        }
        List<String> msgIds = results.stream().flatMap(r -> r.msgIds().stream()).toList();
        PushResult worst = null;
        for (PushStatus s : List.of(PushStatus.FAILED, PushStatus.RATE_LIMITED, PushStatus.SENT, PushStatus.SKIPPED)) {
            worst = results.stream().filter(r -> r.status() == s).findFirst().orElse(null);
            if (worst != null) {
                break;
            }
        }
        return new PushResult(worst.status(), msgIds, worst.errorCode(), worst.reason());
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
