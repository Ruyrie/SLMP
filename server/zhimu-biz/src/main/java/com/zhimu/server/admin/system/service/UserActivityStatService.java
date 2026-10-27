package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.mapper.UserActivityStatMapper;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserActivityStatService {
    private final UserActivityStatMapper mapper;

    /** userId 来自已校验的用户上下文，farmId 来自已校验的成员上下文。 */
    public void touch(Long userId, Long farmId) {
        if (userId == null || userId <= 0) return;
        var now = BeijingTime.now();
        TenantSkip.run(() -> mapper.touch(now.toLocalDate(), userId,
                farmId == null || farmId <= 0 ? 0L : farmId, now));
    }

    /** 绝对值重算，重复执行不会增加生产登记次数；整段事务避免部分日统计遮挡实时回退。 */
    @Transactional
    public void rebuildRecent() {
        LocalDate end = BeijingTime.today();
        LocalDate start = end.minusDays(30);
        TenantSkip.run(() -> {
            mapper.backfillActivity(start, end);
            mapper.resetProduction(start, end);
            mapper.aggregateProduction(start, end);
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                mapper.aggregatePlatformDay(date);
            }
        });
    }
}
