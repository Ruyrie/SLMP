package com.zhimu.server.admin.system.support;

import com.zhimu.server.admin.system.service.UserActivityStatService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserActivityInterceptorTest {
    private final UserActivityStatService service = mock(UserActivityStatService.class);
    private final UserActivityInterceptor interceptor = new UserActivityInterceptor(service);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        FarmContext.clear();
        UserContext.clear();
    }

    @Test
    void countsAuthenticatedAppAndWebRequestsOnceIncludingAsyncRedispatch() throws Exception {
        for (ClientType client : new ClientType[]{ClientType.APP_ANDROID, ClientType.WEB}) {
            UserContext.set(new UserContext.LoginUser(606001L, client, 0, "jti"));
            FarmContext.set(new FarmContext.FarmMember(60601L, "测试场", 606001L, 1L,
                    FarmRole.OWNER, Set.of(), FarmStatus.NORMAL));
            var request = new MockHttpServletRequest("GET", "/api/v1/home");
            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
            assertThat(interceptor.preHandle(request, response, handler())).isTrue();
        }
        verify(service, times(2)).touch(606001L, 60601L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void ignoresAnonymousRequestsAndPreflight() throws Exception {
        interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/ping"), response, handler());
        UserContext.set(new UserContext.LoginUser(606001L, ClientType.WEB, 0, "jti"));
        interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/api/v1/home"), response, handler());
        interceptor.preHandle(new MockHttpServletRequest("GET", "/assets/icon.svg"), response, new Object());
        verifyNoInteractions(service);
    }

    @Test
    void doesNotTrustFarmIdHeaderWithoutVerifiedMembership() throws Exception {
        UserContext.set(new UserContext.LoginUser(606001L, ClientType.WEB, 0, "jti"));
        var request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("X-Farm-Id", "99999");
        interceptor.preHandle(request, response, handler());
        verify(service).touch(606001L, null);
    }

    @Test
    void statsFailureDoesNotBlockNormalBusinessRequest() throws Exception {
        UserContext.set(new UserContext.LoginUser(606001L, ClientType.WEB, 0, "jti"));
        doThrow(new IllegalStateException("database unavailable")).when(service).touch(606001L, null);
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/me"), response, handler())).isTrue();
    }

    public void endpoint() {}
    private HandlerMethod handler() throws Exception {
        return new HandlerMethod(this, getClass().getMethod("endpoint"));
    }
}
