package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 管理端登录失败计数与登录成功的会话版本落库。
 * <p>
 * 次数以 Redis admin:login:fail:{username} 为准（30 分钟），并同步 t_admin.login_fail_count；
 * 失败日志用独立事务写入，避免随失败响应回滚。成功登录在同一事务里清零计数、写 last_login 与日志。
 */
@Service
@RequiredArgsConstructor
public class AdminLoginAttemptService {

    public static final String FAIL_KEY_PREFIX = "admin:login:fail:";
    public static final Duration FAIL_TTL = Duration.ofMinutes(30);
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final AdminMapper adminMapper;
    private final OperationLogRecorder operationLogRecorder;

    /** 记录一次密码错误，返回累计次数（第 5 次由调用方转锁定并清零） */
    public int recordFailure(String username) {
        String key = FAIL_KEY_PREFIX + username;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, FAIL_TTL);
        return count == null ? 0 : count.intValue();
    }

    public void clear(String username) {
        redis.delete(FAIL_KEY_PREFIX + username);
    }

    /**
     * 把失败次数与锁定时间同步到 t_admin，并写失败日志（独立事务）。
     * 账号不存在时 adminId 为 null，只写日志。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailure(Long adminId, int failCount, LocalDateTime lockedUntil, OperationLogEntry entry) {
        if (adminId != null) {
            adminMapper.update(new Admin(), Wrappers.<Admin>lambdaUpdate()
                    .eq(Admin::getId, adminId)
                    .set(Admin::getLoginFailCount, failCount)
                    .set(Admin::getLockedUntil, lockedUntil));
        }
        if (entry != null) {
            operationLogRecorder.record(entry);
        }
    }

    /** 登录成功：清零失败计数与锁定时间，写最近登录信息与日志（同一事务） */
    @Transactional
    public void persistSuccess(Long adminId, String ip, String region, OperationLogEntry entry) {
        adminMapper.update(new Admin(), Wrappers.<Admin>lambdaUpdate()
                .eq(Admin::getId, adminId)
                .set(Admin::getLoginFailCount, 0)
                .set(Admin::getLockedUntil, null)
                .set(Admin::getLastLoginAt, BeijingTime.now())
                .set(Admin::getLastLoginIp, ip)
                .set(Admin::getLastLoginRegion, region));
        if (entry != null) {
            operationLogRecorder.record(entry);
        }
    }
}
