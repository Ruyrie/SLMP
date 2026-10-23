package com.zhimu.server.integration.push;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 极光推送配置（zhimu.integration.push）。Master Secret 为空时只写日志、不真实推送。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.push")
public class PushProperties {

    private String appKey;
    @ToString.Exclude
    private String masterSecret;
    private String baseUrl = "https://api.jpush.cn";
    private boolean apnsProduction;
    /** 点击通知打开的 Activity action（App 的 PushRouterActivity） */
    private String intentAction = "com.zhimu.slmp.action.PUSH_OPEN";

    public boolean configured() {
        return appKey != null && !appKey.isBlank() && masterSecret != null && !masterSecret.isBlank();
    }
}
