package com.zhimu.server.framework.security;

import com.zhimu.server.common.error.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 安全相关测试的公共数据 */
public final class SecurityTestData {

    /** 仅测试使用的密钥（不是任何环境的真实密钥） */
    public static final String TEST_SECRET = "unit-test-only-secret-0123456789-abcdef";

    private SecurityTestData() {
    }

    public static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtUserSecret(TEST_SECRET);
        return properties;
    }

    /** 测试用的大号随机用户 ID，避免与他人数据冲突 */
    public static long randomId() {
        return 9_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    }

    /** 只包含给定 Bean（可以为 null 表示没有实现）的 ObjectProvider */
    public static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (bean != null) {
            factory.registerSingleton("bean", bean);
        }
        return factory.getBeanProvider(type);
    }

    public static Object dataOf(BizException e, String key) {
        return e.getData() instanceof Map<?, ?> map ? map.get(key) : null;
    }
}
