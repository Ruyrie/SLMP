package com.zhimu.server.integration.ipregion;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP 属地配置（zhimu.integration.ip2region）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.ip2region")
public class Ip2regionProperties {

    /** ip2region v2 格式 xdb 文件路径；为空或不存在时属地为空 */
    private String dbPath;
}
