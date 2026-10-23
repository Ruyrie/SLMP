package com.zhimu.server.integration.storage;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 课程 HLS 地址配置（zhimu.integration.hls）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.hls")
public class HlsProperties {

    /** HLS 地址前缀：{base-url}/{文件编号}/720p/index.m3u8，对应 zmt-course 桶的 hls/ 前缀 */
    private String baseUrl = "http://127.0.0.1:9000/zmt-course/hls";
}
