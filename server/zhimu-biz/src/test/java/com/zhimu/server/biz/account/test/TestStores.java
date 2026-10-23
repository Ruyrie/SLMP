package com.zhimu.server.biz.account.test;

import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.support.LoginAttemptStore;
import com.zhimu.server.biz.account.support.SmsCodeStore;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 单元测试用的内存存储（不依赖 Redis）。 */
public final class TestStores {

    private TestStores() {
    }

    public static final class InMemorySmsCodeStore implements SmsCodeStore {

        private final Map<String, Instant> cooldown = new HashMap<>();
        private final Map<String, Long> daily = new HashMap<>();
        private final Map<String, Instant> sent = new HashMap<>();
        private final java.util.Set<String> verified = new java.util.HashSet<>();
        private final Map<String, Integer> fails = new HashMap<>();

        @Override
        public long cooldownSeconds(SmsScene scene, String phone) {
            Instant until = cooldown.get(scene.name() + ":" + phone);
            if (until == null) {
                return 0;
            }
            long seconds = Duration.between(Instant.now(), until).toSeconds();
            return Math.max(0, seconds);
        }

        @Override
        public void startCooldown(SmsScene scene, String phone, int seconds) {
            cooldown.put(scene.name() + ":" + phone, Instant.now().plusSeconds(seconds));
        }

        @Override
        public long dailyCount(String phone) {
            return daily.getOrDefault(phone, 0L);
        }

        @Override
        public long increaseDailyCount(String phone) {
            return daily.merge(phone, 1L, Long::sum);
        }

        @Override
        public void markSent(SmsScene scene, String phone, int ttlSeconds) {
            sent.put(scene.name() + ":" + phone, Instant.now().plusSeconds(ttlSeconds));
        }

        @Override
        public boolean isSent(SmsScene scene, String phone) {
            Instant until = sent.get(scene.name() + ":" + phone);
            return until != null && until.isAfter(Instant.now());
        }

        @Override
        public void markVerified(SmsScene scene, String phone, int ttlSeconds) {
            sent.put(scene.name() + ":" + phone, Instant.now().plusSeconds(ttlSeconds));
            verified.add(scene.name() + ":" + phone);
        }

        @Override
        public boolean isVerified(SmsScene scene, String phone) {
            return verified.contains(scene.name() + ":" + phone) && isSent(scene, phone);
        }

        @Override
        public int recordWrongAttempt(SmsScene scene, String phone, int ttlSeconds) {
            return fails.merge(scene.name() + ":" + phone, 1, Integer::sum);
        }

        @Override
        public int wrongAttempts(SmsScene scene, String phone) {
            return fails.getOrDefault(scene.name() + ":" + phone, 0);
        }

        @Override
        public void clear(SmsScene scene, String phone) {
            sent.remove(scene.name() + ":" + phone);
            verified.remove(scene.name() + ":" + phone);
            fails.remove(scene.name() + ":" + phone);
        }

        public void setDailyCount(String phone, long count) {
            daily.put(phone, count);
        }

        public void addWrongAttempts(SmsScene scene, String phone, int count) {
            fails.put(scene.name() + ":" + phone, count);
        }
    }

    public static final class InMemoryLoginAttemptStore implements LoginAttemptStore {

        private final Map<String, Integer> failures = new HashMap<>();
        private final Map<String, Instant> expiresAt = new HashMap<>();

        @Override
        public int failures(String phone) {
            Instant until = expiresAt.get(phone);
            if (until != null && until.isBefore(Instant.now())) {
                failures.remove(phone);
                expiresAt.remove(phone);
                return 0;
            }
            return failures.getOrDefault(phone, 0);
        }

        @Override
        public int recordFailure(String phone, Duration ttl) {
            int count = failures.merge(phone, 1, Integer::sum);
            expiresAt.put(phone, Instant.now().plus(ttl));
            return count;
        }

        @Override
        public long remainingSeconds(String phone) {
            Instant until = expiresAt.get(phone);
            return until == null ? 0 : Math.max(0, Duration.between(Instant.now(), until).toSeconds());
        }

        @Override
        public void clear(String phone) {
            failures.remove(phone);
            expiresAt.remove(phone);
        }

        public void setFailures(String phone, int count) {
            failures.put(phone, count);
            expiresAt.put(phone, Instant.now().plusSeconds(1800));
        }
    }
}
