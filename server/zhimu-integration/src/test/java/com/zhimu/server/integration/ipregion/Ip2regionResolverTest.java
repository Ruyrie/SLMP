package com.zhimu.server.integration.ipregion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Ip2regionResolverTest {

    private static final String DEFAULT_DB = "D:/Android_Program/SLMP/server/tools/ip2region/ip2region.xdb";

    private static String dbPath() {
        String v = System.getenv("IP2REGION_DB");
        return v == null || v.isBlank() ? DEFAULT_DB : v;
    }

    private static Ip2regionResolver resolver(String path) {
        Ip2regionProperties p = new Ip2regionProperties();
        p.setDbPath(path);
        return new Ip2regionResolver(p);
    }

    @Test
    void format_rules() {
        assertThat(Ip2regionResolver.format("中国|0|广东省|深圳市|电信")).isEqualTo("广东省 深圳市");
        assertThat(Ip2regionResolver.format("中国|0|北京|北京市|联通")).isEqualTo("北京市");
        assertThat(Ip2regionResolver.format("中国|0|上海市|上海市|电信")).isEqualTo("上海市");
        assertThat(Ip2regionResolver.format("中国|0|吉林省|吉林市|电信")).isEqualTo("吉林省 吉林市");
        assertThat(Ip2regionResolver.format("中国|0|广东省|0|0")).isEqualTo("广东省");
        assertThat(Ip2regionResolver.format("美国|0|加利福尼亚|0|0")).isEqualTo("美国");
        assertThat(Ip2regionResolver.format("0|0|0|内网IP|内网IP")).isEqualTo("内网");
        assertThat(Ip2regionResolver.format("0|0|0|0|0")).isNull();
    }

    @Test
    void intranetAndInvalid_withoutDb() {
        Ip2regionResolver r = resolver("D:/not-exists/ip2region.xdb");
        assertThat(r.isLoaded()).isFalse();
        assertThat(r.resolve("127.0.0.1")).isEqualTo("内网");
        assertThat(r.resolve("192.168.1.20")).isEqualTo("内网");
        assertThat(r.resolve("10.0.0.8")).isEqualTo("内网");
        assertThat(r.resolve("172.20.1.1")).isEqualTo("内网");
        assertThat(r.resolve("::1")).isEqualTo("内网");
        assertThat(r.resolve("::ffff:192.168.0.1")).isEqualTo("内网");
        assertThat(r.resolve("8.8.8.8")).isNull();
        assertThat(r.resolve("256.1.1.1")).isNull();
        assertThat(r.resolve("abc")).isNull();
        assertThat(r.resolve(null)).isNull();
        assertThat(resolver(null).resolve("8.8.8.8")).isNull();
    }

    @Test
    void resolve_withRealDb() {
        assumeTrue(Files.isRegularFile(Path.of(dbPath())), "ip2region.xdb 不存在，跳过");
        Ip2regionResolver r = resolver(dbPath());
        assertThat(r.isLoaded()).isTrue();
        // 深圳电信 DNS
        assertThat(r.resolve("202.96.134.133")).isEqualTo("广东省 深圳市");
        // 北京联通 DNS（数据为“北京|北京市”）
        assertThat(r.resolve("202.106.0.20")).isEqualTo("北京市");
        assertThat(r.resolve("8.8.8.8")).isEqualTo("美国");
        assertThat(r.resolve("114.114.114.114")).isNotBlank();
        assertThat(r.resolve("172.16.0.1")).isEqualTo("内网");
    }
}
