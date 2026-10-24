package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.vo.RecoveryItemVO;
import com.zhimu.server.biz.account.api.AccountRecoveryAdminService;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryReviewServiceImplTest {

    private final AuditAdminQueryMapper mapper = mock(AuditAdminQueryMapper.class);
    private final RecoveryReviewServiceImpl service = new RecoveryReviewServiceImpl(mapper,
            mock(AccountRecoveryAdminService.class));

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
    }

    @Test
    void 找回队列只显示恢复码正确的申请并打码手机号() {
        RecoveryItemVO row = new RecoveryItemVO();
        row.setId(1L);
        row.setUserName("张秀英");
        row.setOldPhone("13900006615");
        row.setNewPhone("17700000638");
        row.setSubmitChannel("CS_PROXY");
        row.setProxyAdminName("吴昊");
        row.setStatus("PENDING");
        row.setSubmittedAt(LocalDateTime.of(2026, 10, 19, 8, 16));
        row.setCodeAttemptNo(3);
        row.setCodeFailCount(2);
        when(mapper.countRecoveries(any(), any())).thenReturn(1L);
        when(mapper.selectRecoveries(any(), any(), anyInt(), anyInt())).thenReturn(List.of(row));
        when(mapper.countRecoveryByStatus("PENDING")).thenReturn(2L);
        when(mapper.countRecoveryByStatus("APPROVED")).thenReturn(5L);
        when(mapper.countRecoveryByStatus("REJECTED")).thenReturn(1L);

        PageVO<RecoveryItemVO> page = service.list(new RecoveryAdminQuery());

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getTabCounts()).containsEntry("PENDING", 2).containsEntry("HANDLED", 6);
        assertThat(page.getList()).singleElement().satisfies(item -> {
            assertThat(item.getOldPhone()).isEqualTo("139****6615");
            assertThat(item.getNewPhone()).isEqualTo("177****0638");
            assertThat(item.getSubmitChannelText()).isEqualTo("客服代登记 · 吴昊");
            assertThat(item.getStatusText()).isEqualTo("待审核");
            assertThat(item.getWaitText()).isEqualTo("2 小时 14 分");
        });
    }
}
