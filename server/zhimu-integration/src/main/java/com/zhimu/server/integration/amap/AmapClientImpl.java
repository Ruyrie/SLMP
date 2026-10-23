package com.zhimu.server.integration.amap;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.integration.support.HttpClients;
import com.zhimu.server.integration.support.Jsons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 高德 Web 服务 v3：连接与读取超时各 3 秒。日志不记录 Key（请求地址带 Key，不输出地址）。
 */
@Slf4j
public class AmapClientImpl implements AmapClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String MSG_UNAVAILABLE = "地图服务暂时不可用，请稍后重试";

    private final AmapProperties props;
    private final RestClient restClient;

    public AmapClientImpl(AmapProperties props) {
        this.props = props;
        this.restClient = HttpClients.restClient(TIMEOUT, TIMEOUT);
        if (!props.configured()) {
            log.warn("高德 Web 服务 Key 未配置：逆地理编码返回空，行政区划返回 50301");
        }
    }

    @Override
    public boolean isEnabled() {
        return props.configured();
    }

    @Override
    public AmapAddress reverseGeocode(double lng, double lat) {
        if (!props.configured()) {
            return null;
        }
        String location = String.format(Locale.ROOT, "%.6f,%.6f", lng, lat);
        URI uri = uri("/v3/geocode/regeo")
                .queryParam("location", location)
                .queryParam("extensions", "base")
                .queryParam("output", "JSON")
                .encode().build().toUri();
        JsonNode root = get("regeo", uri);
        if (root == null) {
            return null;
        }
        JsonNode regeo = root.get("regeocode");
        JsonNode comp = regeo == null ? null : regeo.get("addressComponent");
        if (comp == null || !comp.isObject()) {
            log.info("高德逆地理编码无结果 location={}", location);
            return null;
        }
        String province = Jsons.nonBlank(comp, "province");
        String city = Jsons.nonBlank(comp, "city");
        return new AmapAddress(province, city != null ? city : province, Jsons.nonBlank(comp, "district"),
                Jsons.nonBlank(comp, "adcode"), Jsons.nonBlank(regeo, "formatted_address"));
    }

    @Override
    public List<AmapDistrict> districts(String keywords, int subdistrict) {
        if (!props.configured()) {
            log.warn("高德行政区划跳过：未配置 Key");
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
        }
        UriComponentsBuilder b = uri("/v3/config/district")
                .queryParam("subdistrict", Math.max(0, Math.min(3, subdistrict)))
                .queryParam("extensions", "base")
                .queryParam("output", "JSON");
        if (keywords != null && !keywords.isBlank()) {
            b.queryParam("keywords", keywords);
        }
        JsonNode root = get("district", b.encode().build().toUri());
        if (root == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_UNAVAILABLE);
        }
        return toDistricts(root.get("districts"));
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(props.getBaseUrl()).path(path).queryParam("key", props.getWebKey());
    }

    /** 成功（status=1）返回 JSON；失败记日志返回 null */
    private JsonNode get(String api, URI uri) {
        long t0 = System.nanoTime();
        ResponseEntity<String> resp;
        try {
            resp = restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                    })
                    .toEntity(String.class);
        } catch (RestClientException e) {
            log.warn("高德{}调用失败 costMs={}：{}", api, costMs(t0),
                    HttpClients.isTimeout(e) ? "超时" : "网络错误 " + e.getClass().getSimpleName());
            return null;
        }
        JsonNode root = Jsons.parse(resp.getBody());
        String status = Jsons.text(root, "status");
        if (!resp.getStatusCode().is2xxSuccessful() || !"1".equals(status)) {
            log.warn("高德{}调用失败 http={} status={} info={} infocode={} costMs={}", api, resp.getStatusCode().value(),
                    status, Jsons.text(root, "info"), Jsons.text(root, "infocode"), costMs(t0));
            return null;
        }
        log.info("高德{}调用成功 infocode={} costMs={}", api, Jsons.text(root, "infocode"), costMs(t0));
        return root;
    }

    private static List<AmapDistrict> toDistricts(JsonNode array) {
        List<AmapDistrict> list = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return list;
        }
        for (JsonNode n : array) {
            list.add(new AmapDistrict(Jsons.nonBlank(n, "adcode"), Jsons.nonBlank(n, "name"),
                    Jsons.nonBlank(n, "level"), Jsons.nonBlank(n, "center"), Jsons.nonBlank(n, "citycode"),
                    toDistricts(n.get("districts"))));
        }
        return list;
    }

    private static long costMs(long t0) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
    }
}
