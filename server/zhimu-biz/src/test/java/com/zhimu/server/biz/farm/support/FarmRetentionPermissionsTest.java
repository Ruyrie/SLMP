package com.zhimu.server.biz.farm.support;

import com.zhimu.server.biz.finance.controller.BusinessReportController;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.BusinessReportService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FarmRetentionPermissionsTest {
    private final FarmMemberCache members = mock(FarmMemberCache.class);
    private final FarmContextInterceptor interceptor = new FarmContextInterceptor(members);
    private final BusinessReportController controller = new BusinessReportController(mock(BusinessReportService.class));

    @AfterEach void clearContext() { UserContext.clear(); FarmContext.clear(); }

    @Test
    void retainedFarmManagerMayExportButCannotRegenerateSummary() throws Exception {
        context(FarmRole.MANAGER, FarmStatus.DISABLED);
        assertThat(interceptor.preHandle(request("/export"), new MockHttpServletResponse(),
                new HandlerMethod(controller, "export", FinanceRequests.ReportExportRequest.class))).isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request("/ai-summary/regenerate"), new MockHttpServletResponse(),
                new HandlerMethod(controller, "regenerate", FinanceRequests.ReportQuery.class)))
                .isInstanceOf(BizException.class).hasMessageContaining("停用");
    }

    @Test
    void retainedFarmStaffDoesNotGainFinancialExportPermission() throws Exception {
        context(FarmRole.STAFF, FarmStatus.DISABLED);
        assertThatThrownBy(() -> interceptor.preHandle(request("/export"), new MockHttpServletResponse(),
                new HandlerMethod(controller, "export", FinanceRequests.ReportExportRequest.class)))
                .isInstanceOf(BizException.class).hasMessageContaining("无权");
    }

    @Test
    void frozenFarmRemainsBlockedForExport() throws Exception {
        context(FarmRole.MANAGER, FarmStatus.FROZEN);
        assertThatThrownBy(() -> interceptor.preHandle(request("/export"), new MockHttpServletResponse(),
                new HandlerMethod(controller, "export", FinanceRequests.ReportExportRequest.class)))
                .isInstanceOf(BizException.class).hasMessageContaining("冻结");
    }

    private void context(FarmRole role, FarmStatus status) {
        UserContext.set(new UserContext.LoginUser(10008L, ClientType.WEB, 0, "retention-test"));
        when(members.get(1007L, 10008L)).thenReturn(new FarmContext.FarmMember(
                1007L, "保留场", 10008L, 5008L, role, Set.of(), status));
    }

    private MockHttpServletRequest request(String suffix) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/business-reports" + suffix);
        request.addHeader("X-Farm-Id", "1007");
        return request;
    }
}
