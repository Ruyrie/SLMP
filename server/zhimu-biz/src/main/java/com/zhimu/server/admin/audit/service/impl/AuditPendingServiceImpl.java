package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.QueueStatRow;
import com.zhimu.server.admin.audit.service.AuditPendingService;
import com.zhimu.server.admin.audit.vo.PendingCountVO;
import com.zhimu.server.admin.audit.vo.QueueCountVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADM-R01：只统计有 VIEW 权限的队列，没有任何审核页 VIEW 权限返回 40308。
 * 找回只计 PENDING（恢复码已核对正确）；超时按提交时间早于当前 −48 小时，恰好 48 小时不算。
 */
@Service
@RequiredArgsConstructor
public class AuditPendingServiceImpl implements AuditPendingService {

    private static final String MSG_NO_PERMISSION = "你没有审核中心的查看权限";

    private final AuditAdminQueryMapper queryMapper;

    @Override
    public PendingCountVO pendingCounts() {
        AdminContext.LoginAdmin admin = AdminContext.get();
        if (admin == null) {
            throw new BizException(ErrorCode.ADMIN_SESSION_TIMEOUT);
        }
        boolean superAdmin = admin.isSuperAdmin();
        boolean farmAudit = superAdmin || admin.hasPermission("WA-R01:VIEW");
        boolean farmChange = superAdmin || admin.hasPermission("WA-R02:VIEW");
        boolean changeRequest = superAdmin || admin.hasPermission("WA-R03:VIEW");
        boolean recovery = superAdmin || admin.hasPermission("WA-R04:VIEW");
        if (!farmAudit && !farmChange && !changeRequest && !recovery) {
            throw new BizException(ErrorCode.ADMIN_PERMISSION_DENIED, MSG_NO_PERMISSION);
        }
        return build(farmAudit, farmChange, changeRequest, recovery);
    }

    @Override
    public PendingCountVO overviewPendingCounts() {
        return build(true, true, true, true);
    }

    private PendingCountVO build(boolean farmAudit, boolean farmChange,
                                 boolean changeRequest, boolean recovery) {
        LocalDateTime overdueBefore = BeijingTime.now().minusHours(48);
        Map<String, QueueStatRow> stats = new LinkedHashMap<>();
        for (QueueStatRow row : queryMapper.selectQueueStats(overdueBefore)) {
            stats.put(row.getQueue(), row);
        }
        List<QueueCountVO> items = new ArrayList<>();
        int overdueTotal = 0;
        PendingCountVO.PendingCountVOBuilder builder = PendingCountVO.builder();
        if (farmAudit) {
            QueueCountVO item = item(stats, "FARM_AUDIT");
            builder.farmAudit(item.getCount());
            items.add(item);
            overdueTotal += item.getOverdueCount();
        }
        if (farmChange) {
            QueueCountVO item = item(stats, "FARM_CHANGE");
            builder.farmChange(item.getCount());
            items.add(item);
            overdueTotal += item.getOverdueCount();
        }
        if (changeRequest) {
            QueueCountVO item = item(stats, "CHANGE_REQUEST");
            builder.changeRequest(item.getCount());
            items.add(item);
            overdueTotal += item.getOverdueCount();
            builder.sellableDateCount((int) queryMapper.countSellableDatePending());
        }
        if (recovery) {
            QueueCountVO item = item(stats, "ACCOUNT_RECOVERY");
            builder.accountRecovery(item.getCount());
            items.add(item);
            overdueTotal += item.getOverdueCount();
        }
        return builder.overdueTotal(overdueTotal).items(items).build();
    }

    private static QueueCountVO item(Map<String, QueueStatRow> stats, String queue) {
        QueueStatRow row = stats.get(queue);
        return QueueCountVO.builder()
                .queue(queue)
                .count(row == null || row.getCount() == null ? 0 : row.getCount().intValue())
                .earliestAt(row == null ? null : row.getEarliestAt())
                .overdueCount(row == null || row.getOverdueCount() == null ? 0 : row.getOverdueCount().intValue())
                .build();
    }
}
