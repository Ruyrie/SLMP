package com.zhimu.server.admin.audit.service.impl;

import com.zhimu.server.admin.audit.mapper.AuditAdminQueryMapper;
import com.zhimu.server.admin.audit.request.ChangeRequestApproveRequest;
import com.zhimu.server.admin.audit.vo.ApproveResultVO;
import com.zhimu.server.biz.common.api.ChangeRequestAdminService;
import com.zhimu.server.biz.common.change.ChangeRequestHandlerRegistry;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeRequestReviewServiceImplTest {

    private final AuditAdminQueryMapper mapper = mock(AuditAdminQueryMapper.class);
    private final ChangeRequestAdminService adminService = mock(ChangeRequestAdminService.class);
    private final ChangeRequestReviewServiceImpl service = new ChangeRequestReviewServiceImpl(mapper,
            adminService, new ChangeRequestHandlerRegistry(List.of()));

    @BeforeEach
    void setUp() {
        AdminContext.set(new AdminContext.LoginAdmin(2L, "吴昊", Set.of("AUDITOR"),
                Set.of("WA-R03:VIEW", "WA-R03:HANDLE"), 0, "tok"));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    @Test
    void 通过后返回重算条数与摘要() {
        ChangeRequest before = request("PENDING");
        ChangeRequest after = request("APPROVED");
        after.setRecalcCount(4);
        after.setRecalcSummary("已重新计算 4 项数据");
        when(mapper.selectChangeRequest(8L)).thenReturn(before, after);
        when(mapper.selectUserName(10001L)).thenReturn("王建国");

        ChangeRequestApproveRequest request = new ChangeRequestApproveRequest();
        request.setReviewComment("  修改原因充分  ");
        request.setVersion(0);

        ApproveResultVO result = service.approve(8L, request);

        verify(adminService).approve(8L, 2L, "修改原因充分", 0);
        assertThat(result.getRecalcCount()).isEqualTo(4);
        assertThat(result.getRecalcSummary()).isEqualTo("已重新计算 4 项数据");
        assertThat(result.getNotifiedNames()).containsExactly("王建国");
    }

    @Test
    void 版本冲突从审核服务原样抛出() {
        when(mapper.selectChangeRequest(8L)).thenReturn(request("PENDING"));
        doThrow(new BizException(ErrorCode.VERSION_CONFLICT)).when(adminService)
                .approve(eq(8L), eq(2L), eq("同意"), eq(9));
        ChangeRequestApproveRequest request = new ChangeRequestApproveRequest();
        request.setReviewComment("同意");
        request.setVersion(9);

        assertThatThrownBy(() -> service.approve(8L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40902));
    }

    private static ChangeRequest request(String status) {
        ChangeRequest request = new ChangeRequest();
        request.setId(8L);
        request.setRequestNo("CR2610190008");
        request.setBizType("FEED_RECORD");
        request.setBizId(52031L);
        request.setFarmId(1001L);
        request.setSubmittedBy(10001L);
        request.setStatus(status);
        request.setVersion(0);
        request.setFieldName("实际投喂量");
        request.setChangeType("UPDATE");
        return request;
    }
}
