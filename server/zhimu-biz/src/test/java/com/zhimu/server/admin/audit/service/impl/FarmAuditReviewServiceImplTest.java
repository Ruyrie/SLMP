package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.query.FarmAuditAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.vo.FarmAuditItemVO;
import com.zhimu.server.admin.audit.vo.FarmAuditPageVO;
import com.zhimu.server.admin.base.api.BaseDataQueryService;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.biz.farm.api.FarmAuditAdminService;
import com.zhimu.server.biz.farm.entity.Farm;
import com.zhimu.server.biz.farm.entity.FarmAudit;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmAuditReviewServiceImplTest {

    private final AuditAdminQueryMapper queryMapper = mock(AuditAdminQueryMapper.class);
    private final FarmAuditAdminService auditAdminService = mock(FarmAuditAdminService.class);
    private final BaseDataQueryService baseData = mock(BaseDataQueryService.class);
    private final FarmAuditReviewServiceImpl service = new FarmAuditReviewServiceImpl(queryMapper,
            auditAdminService, baseData, mock(FileVoAssembler.class));

    @BeforeEach
    void setUp() {
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-19T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
        AdminContext.set(new AdminContext.LoginAdmin(2L, "吴昊", Set.of("AUDITOR"),
                Set.of("WA-R01:VIEW", "WA-R01:HANDLE"), 0, "tok"));
    }

    @AfterEach
    void tearDown() {
        BeijingTime.setClock(null);
        AdminContext.clear();
    }

    @Test
    void 列表分页_打码_超时与tab_counts() {
        FarmAuditItemVO row = new FarmAuditItemVO();
        row.setAuditId(3001L);
        row.setFarmId(1003L);
        row.setFarmName("北湾牛场");
        row.setOwnerPhone("13800006620");
        row.setStatus("PENDING");
        row.setSubmittedAt(LocalDateTime.of(2026, 10, 17, 9, 0));
        when(queryMapper.countFarmAudits(any(), any(), any(), any(), any()))
                .thenReturn(12L, 12L, 3L, 1L);
        when(queryMapper.selectFarmAudits(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));
        when(queryMapper.countOverdueFarmAudits(any(), any(), any(), any(), any()))
                .thenReturn(2L);
        when(queryMapper.selectOverdueFarmNames(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of("北湾牛场"));

        FarmAuditAdminQuery query = new FarmAuditAdminQuery();
        query.setPage(2);
        query.setSize(10);
        FarmAuditPageVO page = service.list(query);

        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotal()).isEqualTo(12);
        assertThat(page.getList()).singleElement().satisfies(item -> {
            assertThat(item.getOwnerPhone()).isEqualTo("138****6620");
            assertThat(item.getStatusText()).isEqualTo("待审核");
            assertThat(item.getWaitText()).isEqualTo("2 天 1 小时");
            assertThat(item.getOverdue()).isTrue();
        });
        assertThat(page.getTabCounts()).containsEntry("PENDING", 12)
                .containsEntry("APPROVED", 3).containsEntry("REJECTED", 1);
        assertThat(page.getOverdueCount()).isEqualTo(2);
        assertThat(page.getOverdueFarmNames()).containsExactly("北湾牛场");
    }

    @Test
    void 通过时版本冲突从模块02服务原样抛出() {
        FarmAudit audit = new FarmAudit();
        audit.setId(3002L);
        audit.setFarmId(1003L);
        audit.setStatus("PENDING");
        Farm farm = new Farm();
        farm.setId(1003L);
        farm.setName("北湾牛场");
        farm.setOwnerUserId(10001L);
        User owner = new User();
        owner.setId(10001L);
        owner.setName("王建国");
        when(queryMapper.selectFarmAudit(3002L)).thenReturn(audit);
        when(queryMapper.selectFarmIncludingDeleted(1003L)).thenReturn(farm);
        when(queryMapper.selectUser(10001L)).thenReturn(owner);
        doThrow(new BizException(ErrorCode.VERSION_CONFLICT)).when(auditAdminService)
                .approve(eq(3002L), eq(2L), eq("资料齐全"), eq(1));

        AuditApproveRequest request = new AuditApproveRequest();
        request.setAuditOpinion("  资料齐全  ");
        request.setVersion(1);

        assertThatThrownBy(() -> service.approve(3002L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40902));
        verify(auditAdminService).approve(3002L, 2L, "资料齐全", 1);
    }

    @Test
    void 通过意见为空返回40004() {
        AuditApproveRequest request = new AuditApproveRequest();
        request.setAuditOpinion(" ");
        request.setVersion(1);
        assertThatThrownBy(() -> service.approve(3002L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40004));
    }
}
