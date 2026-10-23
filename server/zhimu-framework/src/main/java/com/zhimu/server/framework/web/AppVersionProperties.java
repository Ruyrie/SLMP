package com.zhimu.server.framework.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 最低支持版本：App 请求的 X-App-Version 低于 minVersion 时，响应头返回 X-Min-App-Version 与 X-App-Download-Url。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "zhimu.app-version")
public class AppVersionProperties {

    /** 主.次.修订，例如 1.0.0；为空不提示 */
    private String minVersion = "1.0.0";

    /** 下载地址；为空时不返回 X-App-Download-Url */
    private String downloadUrl;
}
