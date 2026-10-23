package com.zhimu.server.framework.web;

import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.context.TraceContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceFilterTest {

    private static TraceFilter filter(String minVersion, String downloadUrl) {
        AppVersionProperties properties = new AppVersionProperties();
        properties.setMinVersion(minVersion);
        properties.setDownloadUrl(downloadUrl);
        return new TraceFilter(properties);
    }

    @Test
    void 生成trace_id并写入请求上下文_结束后全部清理() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader("X-Client", "APP_ANDROID");
        request.addHeader("X-App-Version", "1.3.0");
        request.addHeader("X-Forwarded-For", "10.0.0.8, 172.16.0.1");
        request.addHeader("User-Agent", "okhttp/4.12");
        request.addHeader("Idempotency-Key", "submit-001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInChain = new AtomicReference<>();
        AtomicReference<String> mdcInChain = new AtomicReference<>();
        AtomicReference<RequestContext.Info> infoInChain = new AtomicReference<>();

        filter("1.2.0", "https://example.com/app.apk").doFilter(request, response, (req, resp) -> {
            traceInChain.set(TraceContext.get());
            mdcInChain.set(MDC.get("trace_id"));
            infoInChain.set(RequestContext.get());
            UserContext.set(new UserContext.LoginUser(1L, ClientType.APP_ANDROID, 0, "j"));
            MDC.put("user_id", "1");
        });

        assertThat(traceInChain.get()).matches("[0-9a-f]{32}");
        assertThat(mdcInChain.get()).isEqualTo(traceInChain.get());
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceInChain.get());
        RequestContext.Info info = infoInChain.get();
        assertThat(info.clientType()).isEqualTo(ClientType.APP_ANDROID);
        assertThat(info.appVersion()).isEqualTo("1.3.0");
        assertThat(info.ip()).isEqualTo("10.0.0.8");
        assertThat(info.userAgent()).isEqualTo("okhttp/4.12");
        assertThat(info.idempotencyKey()).isEqualTo("submit-001");
        assertThat(response.getHeader("X-Min-App-Version")).isNull();

        assertThat(TraceContext.get()).isNull();
        assertThat(RequestContext.get()).isNull();
        assertThat(UserContext.get()).isNull();
        assertThat(FarmContext.get()).isNull();
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("user_id")).isNull();
    }

    @Test
    void 低版本App返回最低版本与下载地址() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader("X-Client", "APP_ANDROID");
        request.addHeader("X-App-Version", "1.1.9-debug");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter("1.2.0", "https://example.com/app.apk").doFilter(request, response, (req, resp) -> {
        });

        assertThat(response.getHeader("X-Min-App-Version")).isEqualTo("1.2.0");
        assertThat(response.getHeader("X-App-Download-Url")).isEqualTo("https://example.com/app.apk");
    }

    @Test
    void 下载地址为空时只返回最低版本() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader("X-Client", "APP_ANDROID");
        request.addHeader("X-App-Version", "0.9.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter("1.0.0", "").doFilter(request, response, (req, resp) -> {
        });

        assertThat(response.getHeader("X-Min-App-Version")).isEqualTo("1.0.0");
        assertThat(response.containsHeader("X-App-Download-Url")).isFalse();
    }

    @Test
    void Web端与无效X_Client不提示版本_IP依次退回() throws Exception {
        MockHttpServletRequest web = new MockHttpServletRequest("GET", "/api/v1/ping");
        web.addHeader("X-Client", "WEB");
        web.addHeader("X-App-Version", "0.1.0");
        web.addHeader("X-Real-IP", "192.168.1.20");
        MockHttpServletResponse webResponse = new MockHttpServletResponse();
        AtomicReference<RequestContext.Info> info = new AtomicReference<>();

        filter("1.0.0", "https://example.com/app.apk").doFilter(web, webResponse, (req, resp) -> info.set(RequestContext.get()));

        assertThat(webResponse.getHeader("X-Min-App-Version")).isNull();
        assertThat(info.get().clientType()).isEqualTo(ClientType.WEB);
        assertThat(info.get().ip()).isEqualTo("192.168.1.20");

        MockHttpServletRequest unknown = new MockHttpServletRequest("GET", "/api/v1/ping");
        unknown.addHeader("X-Client", "IOS");
        unknown.setRemoteAddr("127.0.0.9");
        filter("1.0.0", null).doFilter(unknown, new MockHttpServletResponse(), (req, resp) -> info.set(RequestContext.get()));

        assertThat(info.get().clientType()).isNull();
        assertThat(info.get().ip()).isEqualTo("127.0.0.9");
    }

    @Test
    void 下游抛异常时同样清理上下文() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter("1.0.0", null).doFilter(request, response, (req, resp) -> {
            UserContext.set(new UserContext.LoginUser(1L, ClientType.WEB, 0, "j"));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(UserContext.get()).isNull();
        assertThat(TraceContext.get()).isNull();
        assertThat(response.getHeader("X-Trace-Id")).matches("[0-9a-f]{32}");
    }

    @Test
    void 版本号按主次修订逐段比较() {
        assertThat(AppVersions.compare("1.2.3-debug", "1.2.3")).isZero();
        assertThat(AppVersions.compare("1.10.0", "1.9.9")).isPositive();
        assertThat(AppVersions.compare("1.2", "1.2.0")).isZero();
        assertThat(AppVersions.compare("1.1.9", "1.2.0")).isNegative();
    }
}
