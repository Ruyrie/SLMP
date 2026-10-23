package com.zhimu.server.integration.amap;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高德 Web 服务配置（zhimu.integration.amap）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.amap")
public class AmapProperties {

    @ToString.Exclude
    private String webKey;
    private String baseUrl = "https://restapi.amap.com";

    public boolean configured() {
        return webKey != null && !webKey.isBlank();
    }
}
