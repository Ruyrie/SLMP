package com.zhimu.server.framework.idempotent;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import com.zhimu.server.framework.security.RedisTestSupport;
import com.zhimu.server.framework.security.SecurityTestData;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IdempotentAspectTest {

    private static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    @Getter
    @Setter
    public static class RecordVO {
        private Long recordId;
        private BigDecimal qty;
        @ManagerView
        private BigDecimal amount;
        private LocalDateTime createdAt;
        private Boolean isLocked;
    }

    public static class RecordController {

        final AtomicInteger calls = new AtomicInteger();

        @Idempotent
        public ApiResponse<RecordVO> create() {
            calls.incrementAndGet();
            RecordVO vo = new RecordVO();
            vo.setRecordId(100L + calls.get());
            vo.setQty(new BigDecimal("12.30"));
            vo.setAmount(new BigDecimal("99.90"));
            vo.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 30, 0));
            vo.setIsLocked(false);
            return ApiResponse.ok(vo);
        }

        @Idempotent(required = true)
        public ApiResponse<Void> mustHaveKey() {
            calls.incrementAndGet();
            return ApiResponse.ok();
        }

        @Idempotent
        public ApiResponse<Void> failing() {
            calls.incrementAndGet();
            throw new BizException(ErrorCode.CAPACITY_CONFIRM).with("need_confirm", true);
        }

        @Idempotent
        public ApiResponse<Void> failedResponse() {
            calls.incrementAndGet();
            return ApiResponse.fail(42206, "超出栏舍容量", null);
        }

        @Idempotent
        public Map<String, Object> plainMap() {
            calls.incrementAndGet();
            return Map.of("count", calls.get());
        }
    }

    private final long userId = SecurityTestData.randomId();
    private IdempotencyService service;
    private RecordController target;
    private RecordController proxy;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(RedisTestSupport.available(), "本机 Redis 未启动，跳过");
    }

    private RecordController proxy() {
        if (proxy == null) {
            service = new IdempotencyService(RedisTestSupport.template());
            target = new RecordController();
            AspectJProxyFactory factory = new AspectJProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAspect(new IdempotentAspect(service, MAPPER));
            proxy = factory.getProxy();
        }
        return proxy;
    }

    private static void request(String key) {
        RequestContext.set(new RequestContext.Info(ClientType.APP_ANDROID, "1.0.0", "127.0.0.1", "test", key));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        UserContext.clear();
        AdminContext.clear();
        FarmContext.clear();
        RedisTestSupport.deleteByPattern("idem:" + userId + ":*");
        RedisTestSupport.deleteByPattern("idem:a" + userId + ":*");
    }

    @Test
    void 重复请求返回第一次的结果() throws Exception {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        request("submit-1");

        ApiResponse<RecordVO> first = proxy().create();
        ApiResponse<RecordVO> second = proxy.create();

        assertThat(target.calls.get()).isEqualTo(1);
        assertThat(second.getCode()).isZero();
        String firstJson = MAPPER.writeValueAsString(first.getData());
        String secondJson = MAPPER.writeValueAsString(second.getData());
        assertThat(secondJson).isEqualTo(firstJson)
                .contains("\"record_id\":101").contains("\"qty\":12.30").contains("\"amount\":99.90")
                .contains("\"created_at\":\"2026-10-19 08:30:00\"").contains("\"is_locked\":false");

        IdempotencyService.IdempotencyResult result = service.findResult(userId, "submit-1");
        assertThat(result.done()).isTrue();
        assertThat(result.response().get("code")).isEqualTo(0);
        assertThat(service.findResult(userId, "other")).isNull();
    }

    @Test
    void 处理中重复提交返回40901() {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        request("submit-2");
        proxy();
        assertThat(service.tryStart("idem:" + userId + ":submit-2")).isTrue();

        assertThatThrownBy(() -> proxy.create()).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(40901));
        assertThat(target.calls.get()).isZero();
        assertThat(service.findResult(userId, "submit-2").done()).isFalse();
    }

    @Test
    void 业务错误不缓存_可以用同一个键重试() {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        request("submit-3");

        assertThatThrownBy(() -> proxy().failing()).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> proxy.failing()).isInstanceOf(BizException.class);
        assertThat(target.calls.get()).isEqualTo(2);
        assertThat(service.findResult(userId, "submit-3")).isNull();

        request("submit-4");
        proxy.failedResponse();
        proxy.failedResponse();
        assertThat(target.calls.get()).isEqualTo(4);
    }

    @Test
    void 没有幂等键时_required为false照常执行_为true返回40001() {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        request(null);

        proxy().create();
        proxy.create();
        assertThat(target.calls.get()).isEqualTo(2);

        assertThatThrownBy(() -> proxy.mustHaveKey()).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getCode()).isEqualTo(40001));
    }

    @Test
    void 管理员使用a前缀_Map返回值原样重放() {
        AdminContext.set(new AdminContext.LoginAdmin(userId, "吴昊", Set.of(), Set.of(), 0, "tok"));
        request("admin-1");

        Map<String, Object> first = proxy().plainMap();
        Map<String, Object> second = proxy.plainMap();

        assertThat(target.calls.get()).isEqualTo(1);
        assertThat(second).isEqualTo(first);
        assertThat(RedisTestSupport.template().hasKey("idem:a" + userId + ":admin-1")).isTrue();
    }

    @Test
    void 员工第一次拿到的是裁剪后的结果_重放一致() throws Exception {
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 0, "j"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", userId, 1L, FarmRole.STAFF, Set.of(), FarmStatus.NORMAL));
        request("staff-1");

        proxy().create();
        ApiResponse<RecordVO> replay = proxy.create();

        assertThat(MAPPER.writeValueAsString(replay)).doesNotContain("amount").contains("\"qty\":12.30");
    }
}
