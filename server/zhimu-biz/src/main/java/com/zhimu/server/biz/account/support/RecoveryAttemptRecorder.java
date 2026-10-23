package com.zhimu.server.biz.account.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.entity.RecoveryVerifyLog;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.RecoveryVerifyResult;
import com.zhimu.server.biz.account.mapper.RecoveryVerifyLogMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 恢复码校验失败记录：计数与校验记录在独立事务中先提交，再抛出业务异常（模块01 文档 2.10）。
 */
@Component
@RequiredArgsConstructor
public class RecoveryAttemptRecorder {

    private final UserMapper userMapper;
    private final RecoveryVerifyLogMapper verifyLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(User user, String oldPhone, RecoveryVerifyResult result, int failCountAfter,
                              LocalDateTime failFirstAt, LocalDateTime lockUntil, Integer remainingAttempts,
                              ClientType client) {
        if (user != null && result != RecoveryVerifyResult.PHONE_NOT_REGISTERED) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, user.getId())
                    .set(User::getRecoveryFailCount, failCountAfter)
                    .set(User::getRecoveryFailFirstAt, failFirstAt)
                    .set(User::getRecoveryLockUntil, lockUntil)
                    .set(User::getUpdatedAt, com.zhimu.server.common.util.BeijingTime.now()));
        }
        RecoveryVerifyLog log = new RecoveryVerifyLog();
        log.setUserId(user == null ? null : user.getId());
        log.setOldPhone(oldPhone);
        log.setResult(result.name());
        log.setFailCountAfter(failCountAfter);
        log.setRemainingAttempts(remainingAttempts);
        log.setLockUntil(lockUntil);
        log.setSubmitChannel(client == null ? ClientType.APP_ANDROID.name() : client.name());
        log.setIp(RequestContext.ip());
        verifyLogMapper.insert(log);
    }
}
