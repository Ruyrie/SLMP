package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.QueueStatRow;
import com.zhimu.server.admin.audit.vo.PendingCountVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditPendingServiceImplTest {

    private final AuditAdminQueryMapper mapper = mock(AuditAdminQueryMapper.class);
    private final AuditPendingServiceImpl service = new AuditPendingServiceImpl(mapper);

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void 没有审核页查看权限返回40308() {
        AdminContext.set(new AdminContext.LoginAdmin(9L, "郑琳", Set.of("OPERATOR"),
                Set.of("WA-S02:VIEW"), 0, "tok"));
        assertThatThrownBy(service::pendingCounts)
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40308));
    }

    @Test
    void 只返回有VIEW权限的队列并汇总超时() {
        AdminContext.set(new AdminContext.LoginAdmin(2L, "吴昊", Set.of("AUDITOR"),
                Set.of("WA-R01:VIEW"), 0, "tok"));
        QueueStatRow audit = row("FARM_AUDIT", 12, 2);
        QueueStatRow change = row("FARM_CHANGE", 3, 1);
        QueueStatRow request = row("CHANGE_REQUEST", 8, 0);
        QueueStatRow recovery = row("ACCOUNT_RECOVERY", 2, 0);
        when(mapper.selectQueueStats(any())).thenReturn(List.of(audit, change, request, recovery));

        PendingCountVO result = service.pendingCounts();

        assertThat(result.getFarmAudit()).isEqualTo(12);
        assertThat(result.getFarmChange()).isNull();
        assertThat(result.getChangeRequest()).isNull();
        assertThat(result.getAccountRecovery()).isNull();
        assertThat(result.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getQueue()).isEqualTo("FARM_AUDIT"));
        assertThat(result.getOverdueTotal()).isEqualTo(2);
    }

    @Test
    void 平台概览返回全部四类数字() {
        when(mapper.selectQueueStats(any())).thenReturn(List.of(
                row("FARM_AUDIT", 1, 0), row("FARM_CHANGE", 2, 0),
                row("CHANGE_REQUEST", 3, 0), row("ACCOUNT_RECOVERY", 4, 0)));
        when(mapper.countSellableDatePending()).thenReturn(1L);

        PendingCountVO result = service.overviewPendingCounts();

        assertThat(result.getFarmAudit()).isEqualTo(1);
        assertThat(result.getFarmChange()).isEqualTo(2);
        assertThat(result.getChangeRequest()).isEqualTo(3);
        assertThat(result.getAccountRecovery()).isEqualTo(4);
        assertThat(result.getSellableDateCount()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(4);
    }

    private static QueueStatRow row(String queue, long count, long overdue) {
        QueueStatRow row = new QueueStatRow();
        row.setQueue(queue);
        row.setCount(count);
        row.setOverdueCount(overdue);
        row.setEarliestAt(LocalDateTime.of(2026, 10, 16, 7, 50));
        return row;
    }
}
