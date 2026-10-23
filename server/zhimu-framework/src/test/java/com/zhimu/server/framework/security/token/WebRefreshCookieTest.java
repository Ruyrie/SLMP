package com.zhimu.server.framework.security.token;

import com.zhimu.server.common.context.RequestContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.framework.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WebRefreshCookieTest {

    private final SecurityProperties properties = new SecurityProperties();
    private final WebRefreshCookie cookie = new WebRefreshCookie(properties);

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void 下发刷新令牌Cookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookie.write(response, "refresh-token-value");

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("ZMT_REFRESH=refresh-token-value")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=2592000")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }

    @Test
    void 开发环境不加Secure_清除时Max_Age为0() {
        properties.setCookieSecure(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookie.clear(response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("ZMT_REFRESH=;").contains("Max-Age=0").contains("Path=/api/v1/auth")
                .doesNotContain("Secure");
    }

    @Test
    void 读取Cookie与判断Web端() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(cookie.read(request)).isNull();
        request.setCookies(new Cookie("ZMT_REFRESH", "abc"));
        assertThat(cookie.read(request)).isEqualTo("abc");

        assertThat(cookie.isWebClient()).isFalse();
        RequestContext.set(new RequestContext.Info(ClientType.WEB, null, null, null, null));
        assertThat(cookie.isWebClient()).isTrue();
    }
}
