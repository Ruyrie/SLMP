package com.zhimu.server.integration.sms;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 短信配置（zhimu.integration.sms）。
 */
@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "zhimu.integration.sms")
public class SmsProperties {

    private SmsMode mode = SmsMode.MOCK;
    /** mock 模式的固定验证码 */
    private String mockCode = "123456";
    /** 号码认证服务地址（主机名） */
    private String endpoint = "dypnsapi.aliyuncs.com";
    @ToString.Exclude
    private String accessKeyId;
    @ToString.Exclude
    private String accessKeySecret;
    private String signName;
    private int codeLength = 6;
    private int validSeconds = 300;
    private int intervalSeconds = 60;
    /** 模板枚举名 → 阿里云模板号 */
    private Map<String, String> templates = new LinkedHashMap<>();

    public String templateCode(SmsTemplate template) {
        return templates.get(template.name());
    }

    /** aliyun 模式所需的 Key 与签名都已配置 */
    public boolean aliyunConfigured() {
        return hasText(accessKeyId) && hasText(accessKeySecret) && hasText(signName);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
