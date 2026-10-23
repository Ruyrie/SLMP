package com.zhimu.server.admin.system.support;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 联调开关：只有 dev profile 才在 ADM-S22 / S24 的响应中返回 temp_password；
 * 生产（prod / 未激活 dev）不返回，也不写日志、不发短信。短信发送留给后续批次。
 */
@Component
@RequiredArgsConstructor
public class AdminDevSupport {

    private final Environment environment;

    public boolean exposeTempPassword() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile.trim()));
    }
}
