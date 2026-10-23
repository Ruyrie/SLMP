package com.zhimu.server.integration.amap;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 高德：逆地理编码解析（直辖市 city 为 []）、失败返回 null、行政区划树（本地假服务）。
 */
class AmapClientImplTest {

    private static final String REGEO = "{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\",\"regeocode\":{"
            + "\"formatted_address\":\"广东省深圳市南山区粤海街道科技园\",\"addressComponent\":{\"country\":\"中国\","
            + "\"province\":\"广东省\",\"city\":\"深圳市\",\"citycode\":\"0755\",\"district\":\"南山区\","
            + "\"adcode\":\"440305\",\"township\":\"粤海街道\"}}}";
    private static final String REGEO_BEIJING = "{\"status\":\"1\",\"infocode\":\"10000\",\"regeocode\":{"
            + "\"formatted_address\":\"北京市东城区东华门街道天安门\",\"addressComponent\":{\"province\":\"北京市\","
            + "\"city\":[],\"district\":\"东城区\",\"adcode\":\"110101\"}}}";
    private static final String DISTRICT = "{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\",\"districts\":[{"
            + "\"citycode\":[],\"adcode\":\"440000\",\"name\":\"广东省\",\"center\":\"113.266887,23.133306\","
            + "\"level\":\"province\",\"districts\":[{\"citycode\":\"0755\",\"adcode\":\"440300\",\"name\":\"深圳市\","
            + "\"center\":\"114.057939,22.543527\",\"level\":\"city\",\"districts\":[]}]}]}";

    private FakeHttpServer server;
    private AmapProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = FakeHttpServer.start((r, ex) -> FakeHttpServer.json(ex, 200, REGEO));
        props = new AmapProperties();
        props.setWebKey("test-amap-key");
        props.setBaseUrl(server.url());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void reverseGeocode_parsesAddress() {
        AmapAddress a = new AmapClientImpl(props).reverseGeocode(113.9437, 22.5409);
        assertThat(a).isEqualTo(new AmapAddress("广东省", "深圳市", "南山区", "440305", "广东省深圳市南山区粤海街道科技园"));
        FakeHttpServer.Recorded req = server.requests().getFirst();
        assertThat(req.path()).isEqualTo("/v3/geocode/regeo");
        String q = URLDecoder.decode(req.rawQuery(), StandardCharsets.UTF_8);
        assertThat(q).contains("key=test-amap-key", "location=113.943700,22.540900", "extensions=base");
    }

    @Test
    void reverseGeocode_municipalityCityFallsBackToProvince() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 200, REGEO_BEIJING));
        AmapAddress a = new AmapClientImpl(props).reverseGeocode(116.397, 39.909);
        assertThat(a.city()).isEqualTo("北京市");
        assertThat(a.district()).isEqualTo("东城区");
    }

    @Test
    void reverseGeocode_failuresReturnNull() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 200,
                "{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}"));
        assertThat(new AmapClientImpl(props).reverseGeocode(1, 1)).isNull();

        server.handler((r, ex) -> FakeHttpServer.json(ex, 500, "oops"));
        assertThat(new AmapClientImpl(props).reverseGeocode(1, 1)).isNull();

        server.handler((r, ex) -> {
            Thread.sleep(4000);
            FakeHttpServer.json(ex, 200, REGEO);
        });
        long t0 = System.currentTimeMillis();
        assertThat(new AmapClientImpl(props).reverseGeocode(1, 1)).isNull();
        assertThat(System.currentTimeMillis() - t0).isLessThan(3800);

        AmapProperties noKey = new AmapProperties();
        noKey.setBaseUrl(server.url());
        assertThat(new AmapClientImpl(noKey).reverseGeocode(1, 1)).isNull();
    }

    @Test
    void districts_tree() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 200, DISTRICT));
        List<AmapDistrict> list = new AmapClientImpl(props).districts("广东", 1);
        assertThat(list).hasSize(1);
        AmapDistrict gd = list.getFirst();
        assertThat(gd.name()).isEqualTo("广东省");
        assertThat(gd.citycode()).isNull();
        assertThat(gd.children()).extracting(AmapDistrict::name).containsExactly("深圳市");
        assertThat(gd.children().getFirst().citycode()).isEqualTo("0755");
        String q = URLDecoder.decode(server.requests().getFirst().rawQuery(), StandardCharsets.UTF_8);
        assertThat(q).contains("keywords=广东", "subdistrict=1");
    }

    @Test
    void districts_failureOrNoKey_50301() {
        server.handler((r, ex) -> FakeHttpServer.json(ex, 200, "{\"status\":\"0\",\"info\":\"DAILY_QUERY_OVER_LIMIT\"}"));
        assertThatThrownBy(() -> new AmapClientImpl(props).districts("广东", 1))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
        assertThatThrownBy(() -> new AmapClientImpl(new AmapProperties()).districts("广东", 1))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(50301));
    }
}
