package com.zhimu.server.integration.ipregion;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ip2region 2.7.0：启动时把整个 xdb 文件载入内存（约 11MB），查询无磁盘 IO、线程安全。只支持 IPv4。
 * 数据格式为“国家|区域|省份|城市|ISP”，空项为 0。
 */
@Slf4j
public class Ip2regionResolver implements IpRegionResolver {

    public static final String INTRANET = "内网";
    private static final String CHINA = "中国";

    private final Searcher searcher;

    public Ip2regionResolver(Ip2regionProperties props) {
        this.searcher = load(props.getDbPath());
    }

    public boolean isLoaded() {
        return searcher != null;
    }

    private static Searcher load(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            log.warn("未配置 ip2region 数据文件（IP2REGION_DB），IP 属地将为空");
            return null;
        }
        if (!Files.isRegularFile(Path.of(dbPath))) {
            log.warn("ip2region 数据文件不存在：{}，IP 属地将为空", dbPath);
            return null;
        }
        try {
            byte[] content = Searcher.loadContentFromFile(dbPath);
            Searcher s = Searcher.newWithBuffer(content);
            log.info("ip2region 已加载 {}（{} 字节）", dbPath, content.length);
            return s;
        } catch (Exception e) {
            log.warn("ip2region 数据文件加载失败：{}，IP 属地将为空：{}", dbPath, e.toString());
            return null;
        }
    }

    @Override
    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String s = ip.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("::ffff:")) {
            s = s.substring("::ffff:".length());
        }
        long v4 = parseIpv4(s);
        if (v4 < 0) {
            return isIntranetV6(s) ? INTRANET : null;
        }
        if (isIntranetV4(v4)) {
            return INTRANET;
        }
        if (searcher == null) {
            return null;
        }
        try {
            return format(searcher.search(v4));
        } catch (Exception e) {
            log.debug("IP 属地解析失败 ip={}：{}", ip, e.toString());
            return null;
        }
    }

    /** “中国|0|广东省|深圳市|电信” → “广东省 深圳市” */
    static String format(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String[] parts = region.split("\\|", -1);
        String country = part(parts, 0);
        String province = part(parts, 2);
        String city = part(parts, 3);
        if ("内网IP".equals(province) || "内网IP".equals(city)) {
            return INTRANET;
        }
        if (country == null) {
            return null;
        }
        if (!CHINA.equals(country) || province == null) {
            return country;
        }
        if (city == null) {
            return province;
        }
        // 直辖市数据为“北京|北京市”，只显示一次
        if (city.startsWith(province)) {
            return city;
        }
        return province + " " + city;
    }

    private static String part(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String v = parts[index].trim();
        return v.isEmpty() || "0".equals(v) ? null : v;
    }

    /** 点分十进制 IPv4 → 无符号整数；不是 IPv4 返回 -1 */
    static long parseIpv4(String s) {
        String[] seg = s.split("\\.", -1);
        if (seg.length != 4) {
            return -1;
        }
        long v = 0;
        for (String part : seg) {
            if (part.isEmpty() || part.length() > 3) {
                return -1;
            }
            int n = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return -1;
                }
                n = n * 10 + (c - '0');
            }
            if (n > 255) {
                return -1;
            }
            v = (v << 8) | n;
        }
        return v;
    }

    /** 10/8、172.16/12、192.168/16、127/8、169.254/16、100.64/10（运营商级 NAT）、0/8 */
    static boolean isIntranetV4(long ip) {
        int a = (int) (ip >>> 24);
        int b = (int) ((ip >>> 16) & 0xFF);
        return a == 10 || a == 127 || a == 0
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168)
                || (a == 169 && b == 254)
                || (a == 100 && b >= 64 && b <= 127);
    }

    private static boolean isIntranetV6(String s) {
        if (!s.contains(":")) {
            return false;
        }
        return "::1".equals(s) || s.startsWith("fe80:") || s.startsWith("fc") || s.startsWith("fd");
    }
}
