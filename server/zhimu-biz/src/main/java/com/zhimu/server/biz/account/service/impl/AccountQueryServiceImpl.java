package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.api.AccountBrief;
import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AccountQueryService 的模块01 实现（供养殖场等模块读取登记人姓名）。 */
@Service
@RequiredArgsConstructor
public class AccountQueryServiceImpl implements AccountQueryService {

    private final UserMapper userMapper;

    @Override
    public AccountBrief findByPhone(String phone) {
        User user = phone == null || phone.isBlank() ? null : userMapper.selectByPhone(phone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            return null;
        }
        return brief(user);
    }

    @Override
    public AccountBrief getBrief(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        return user == null ? null : brief(user);
    }

    @Override
    public Map<Long, AccountBrief> listBriefs(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectByIds(userIds);
        Map<Long, AccountBrief> result = new LinkedHashMap<>();
        for (User user : users) {
            result.put(user.getId(), brief(user));
        }
        return result;
    }

    @Override
    public void recordLastFarm(Long userId, Long farmId) {
        if (userId == null) {
            return;
        }
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getLastFarmId, farmId)
                .set(User::getUpdatedAt, BeijingTime.now()));
    }

    private static AccountBrief brief(User user) {
        boolean cancelled = UserStatus.CANCELLED.name().equals(user.getStatus());
        return new AccountBrief(user.getId(), user.getName(), user.getPhone(), user.getStatus(), cancelled);
    }
}
