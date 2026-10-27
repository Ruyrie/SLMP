package com.zhimu.server.biz.account.support;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serialize short personal-data writes with account cancellation, never model generation. */
@Component
@RequiredArgsConstructor
public class AccountPersonalDataGuard {
    private final UserMapper userMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActive(Long userId) {
        User user = userId == null ? null : userMapper.selectByIdForUpdate(userId);
        return user != null && "NORMAL".equals(user.getStatus());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActive(Long userId) {
        if (!lockActive(userId)) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "ACCOUNT_CANCELLED");
        }
    }
}
