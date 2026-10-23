package com.zhimu.server.framework.lock;

import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.framework.redis.RedisConfig;
import com.zhimu.server.framework.redis.RedisLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link JobLock} 切面：连本机 Redis database 15。
 */
@SpringBootTest(classes = JobLockAspectTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(locations = "classpath:fw-data-test.properties")
class JobLockAspectTest {

    private static final String JOB = "fwtest-demo";
    private static final String KEY = JobLockAspect.KEY_PREFIX + JOB;

    @SpringBootConfiguration
    @ImportAutoConfiguration({DataRedisAutoConfiguration.class, AopAutoConfiguration.class})
    @Import({RedisConfig.class, RedisLock.class, JobLockAspect.class, JobState.class, DemoJob.class})
    static class TestApp {
    }

    static class JobState {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> traceIds = new ArrayList<>();
        int failTimes;
        boolean lockHeldDuringRun;
    }

    static class DemoJob {
        private final JobState state;
        private final StringRedisTemplate redis;

        DemoJob(JobState state, StringRedisTemplate redis) {
            this.state = state;
            this.redis = redis;
        }

        @JobLock(value = JOB, leaseSeconds = 60)
        public void run() {
            int call = state.calls.incrementAndGet();
            state.traceIds.add(TraceContext.get());
            state.lockHeldDuringRun = Boolean.TRUE.equals(redis.hasKey(KEY));
            if (call <= state.failTimes) {
                throw new IllegalStateException("第 " + call + " 次执行失败");
            }
        }
    }

    @Autowired
    private DemoJob job;
    @Autowired
    private JobState state;
    @Autowired
    private RedisLock redisLock;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis.delete(KEY);
        state.calls.set(0);
        state.traceIds.clear();
        state.failTimes = 0;
        state.lockHeldDuringRun = false;
    }

    @AfterEach
    void tearDown() {
        redis.delete(KEY);
    }

    @Test
    void runsWhileHoldingLockAndReleasesAfterwards() {
        job.run();

        assertThat(state.calls).hasValue(1);
        assertThat(state.lockHeldDuringRun).isTrue();
        assertThat(state.traceIds.getFirst()).startsWith("job-");
        assertThat(redis.hasKey(KEY)).isFalse();
        assertThat(TraceContext.get()).isNull();
    }

    @Test
    void skipsWhenLockIsHeldByAnotherInstance() {
        String token = redisLock.tryLock(KEY, 60);

        job.run();

        assertThat(state.calls).hasValue(0);
        assertThat(redis.opsForValue().get(KEY)).isEqualTo(token);
    }

    @Test
    void retriesOnceAfterFailure() {
        state.failTimes = 1;

        job.run();

        assertThat(state.calls).hasValue(2);
        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void swallowsSecondFailureAndReleasesLock() {
        state.failTimes = 5;

        assertThatCode(job::run).doesNotThrowAnyException();

        assertThat(state.calls).hasValue(2);
        assertThat(redis.hasKey(KEY)).isFalse();
    }
}
