package com.zhimu.server.framework.ratelimit;

import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.security.RedisTestSupport;
import com.zhimu.server.framework.security.SecurityTestData;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RateLimitAspectTest {

    @Getter
    @Setter
    public static class SmsRequest {
        private String phone;
        private String scene;
    }

    public static class Api {

        final AtomicInteger calls = new AtomicInteger();

        @RateLimit(key = "test-login", limit = 2, by = RateLimit.LimitBy.IP)
        public void login() {
            calls.incrementAndGet();
        }

        @RateLimit(key = "test-sms", limit = 1, by = RateLimit.LimitBy.PARAM, param = "phone")
        public void sms(SmsRequest body) {
            calls.incrementAndGet();
        }

        @RateLimit(key = "test-check", limit = 1, by = RateLimit.LimitBy.PARAM, param = "phone_number")
        public void check(@RequestParam("phone_number") String phoneNumber) {
            calls.incrementAndGet();
        }

        @RateLimit(key = "test-user", limit = 1)
        @RateLimit(key = "test-user-ip", limit = 2, by = RateLimit.LimitBy.IP)
        public void both() {
            calls.incrementAndGet();
        }
    }

    private final String ip = "10.9." + (SecurityTestData.randomId() % 250) + "." + (SecurityTestData.randomId() % 250);
    private final long userId = SecurityTestData.randomId();
    private Api target;
    private Api proxy;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(RedisTestSupport.available(), "本机 Redis 未启动，跳过");
    }

    private Api proxy() {
        if (proxy == null) {
            target = new Api();
            AspectJProxyFactory factory = new AspectJProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAspect(new RateLimitAspect(RedisTestSupport.template()));
            proxy = factory.getProxy();
            RequestContext.set(new RequestContext.Info(ClientType.APP_ANDROID, null, ip, null, null));
        }
        return proxy;
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        UserContext.clear();
        RedisTestSupport.deleteByPattern("rate:test-*");
    }

    @Test
    void 按IP固定窗口计数_超出返回42901() {
        proxy().login();
        proxy.login();

        assertThatThrownBy(() -> proxy.login()).isInstanceOfSatisfying(BizException.class, e -> {
            assertThat(e.getCode()).isEqualTo(42901);
            assertThat((Long) SecurityTestData.dataOf(e, "cooldown_seconds")).isBetween(1L, 60L);
        });
        assertThat(target.calls.get()).isEqualTo(2);
        assertThat(RedisTestSupport.template().getExpire("rate:test-login:" + ip)).isBetween(1L, 60L);
    }

    @Test
    void 按请求体属性限流() {
        SmsRequest a = new SmsRequest();
        a.setPhone("13800000001");
        SmsRequest b = new SmsRequest();
        b.setPhone("13800000002");

        proxy().sms(a);
        assertThatThrownBy(() -> proxy.sms(a)).isInstanceOf(BizException.class);
        proxy.sms(b);
        assertThat(target.calls.get()).isEqualTo(2);
    }

    @Test
    void 按RequestParam参数限流() {
        proxy().check("13800000003");
        assertThatThrownBy(() -> proxy.check("13800000003")).isInstanceOf(BizException.class);
        proxy.check("13800000004");
        assertThat(target.calls.get()).isEqualTo(2);
    }

    @Test
    void 按用户限流_没有用户时退回IP_可重复标注() {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        proxy().both();
        assertThatThrownBy(() -> proxy.both()).isInstanceOf(BizException.class);
        assertThat(RedisTestSupport.template().hasKey("rate:test-user:" + userId)).isTrue();

        UserContext.clear();
        proxy.both();
        assertThat(RedisTestSupport.template().hasKey("rate:test-user:" + ip)).isTrue();
        assertThatThrownBy(() -> proxy.both()).isInstanceOf(BizException.class);
        assertThat(target.calls.get()).isEqualTo(2);
    }
}
