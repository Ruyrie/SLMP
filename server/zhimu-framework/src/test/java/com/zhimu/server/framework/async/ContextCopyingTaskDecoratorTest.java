package com.zhimu.server.framework.async;

import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCopyingTaskDecoratorTest {

    private final ContextCopyingTaskDecorator decorator = new ContextCopyingTaskDecorator();
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(decorator);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        clearAll();
    }

    @Test
    void copiesContextToPoolThreadAndCleansUpAfterwards() throws Exception {
        setAll("trace-001", 7L);

        List<Object> seen = executor.submit(ContextCopyingTaskDecoratorTest::snapshot).get(5, TimeUnit.SECONDS);
        assertThat(seen).containsExactly("trace-001", 7L, 9L, 1001L, "10.0.0.8", "trace-001", "7");

        clearAll();
        List<Object> afterwards = executor.submit(ContextCopyingTaskDecoratorTest::snapshot).get(5, TimeUnit.SECONDS);
        assertThat(afterwards).containsOnlyNulls();
    }

    @Test
    void copiesContextToVirtualThread() throws Exception {
        SimpleAsyncTaskExecutor virtual = new SimpleAsyncTaskExecutor("test-vt-");
        virtual.setVirtualThreads(true);
        virtual.setTaskDecorator(decorator);
        setAll("trace-002", 8L);

        List<Object> seen = virtual.submit(ContextCopyingTaskDecoratorTest::snapshot).get(5, TimeUnit.SECONDS);
        assertThat(seen).containsExactly("trace-002", 8L, 9L, 1001L, "10.0.0.8", "trace-002", "8");
    }

    @Test
    void restoresCallerContextWhenRunInCallerThread() {
        setAll("captured", 1L);
        List<Object> inside = new ArrayList<>();
        Runnable decorated = decorator.decorate(() -> inside.addAll(snapshot()));
        setAll("caller", 2L);

        decorated.run();

        assertThat(inside).startsWith("captured", 1L);
        assertThat(TraceContext.get()).isEqualTo("caller");
        assertThat(UserContext.userId()).isEqualTo(2L);
        assertThat(MDC.get("trace_id")).isEqualTo("caller");
    }

    private static List<Object> snapshot() {
        List<Object> values = new ArrayList<>();
        values.add(TraceContext.get());
        values.add(UserContext.userId());
        values.add(AdminContext.adminId());
        values.add(FarmContext.farmId());
        values.add(RequestContext.ip());
        values.add(MDC.get("trace_id"));
        values.add(MDC.get("user_id"));
        return values;
    }

    private static void setAll(String traceId, Long userId) {
        TraceContext.set(traceId);
        UserContext.set(new UserContext.LoginUser(userId, ClientType.APP_ANDROID, 1, "jti"));
        AdminContext.set(new AdminContext.LoginAdmin(9L, "吴昊", Set.of("AUDITOR"), Set.of(), 1, "s"));
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", userId, 70L, FarmRole.OWNER, Set.of(),
                FarmStatus.NORMAL));
        RequestContext.set(new RequestContext.Info(ClientType.APP_ANDROID, "1.0.0", "10.0.0.8", "okhttp", null));
        MDC.put("trace_id", traceId);
        MDC.put("user_id", String.valueOf(userId));
    }

    private static void clearAll() {
        TraceContext.clear();
        UserContext.clear();
        AdminContext.clear();
        FarmContext.clear();
        RequestContext.clear();
        MDC.clear();
    }
}
