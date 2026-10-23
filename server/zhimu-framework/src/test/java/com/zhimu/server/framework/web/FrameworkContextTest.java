package com.zhimu.server.framework.web;

import com.zhimu.server.framework.exception.GlobalExceptionHandler;
import com.zhimu.server.framework.idempotent.IdempotentAspect;
import com.zhimu.server.framework.jackson.JacksonConfig;
import com.zhimu.server.framework.openapi.OpenApiConfig;
import com.zhimu.server.framework.ratelimit.RateLimitAspect;
import com.zhimu.server.framework.security.SecurityProperties;
import com.zhimu.server.framework.security.token.UserTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 用自动配置加组件扫描（只扫本层的包）启动上下文，检查 Bean 装配、配置绑定与接口文档分组。不连接数据库。
 */
@SpringBootTest(classes = FrameworkContextTest.TestApp.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.data.redis.database=15",
        "zhimu.security.jwt-user-secret=unit-test-only-secret-0123456789-abcdef",
        "zhimu.security.allowed-origins=",
        "zhimu.security.admin-session-ttl=45m"})
@AutoConfigureMockMvc
class FrameworkContextTest {

    /** 不用 @SpringBootConfiguration，避免被其他测试的配置类自动查找命中 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = {TraceFilter.class, JacksonConfig.class, GlobalExceptionHandler.class,
            SecurityProperties.class, IdempotentAspect.class, RateLimitAspect.class, OpenApiConfig.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.zhimu\\.server\\.framework\\..*Test.*"))
    static class TestApp {
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mvc;

    @Test
    void 上下文装配完整() {
        assertThat(context.getBean(UserTokenService.class)).isNotNull();
        assertThat(context.getBean(IdempotentAspect.class)).isNotNull();
        assertThat(context.getBean(RateLimitAspect.class)).isNotNull();
        assertThat(context.getBean(TraceFilter.class)).isNotNull();
        SecurityProperties properties = context.getBean(SecurityProperties.class);
        assertThat(properties.getAllowedOrigins()).isEmpty();
        assertThat(properties.getAdminSessionTtl().toMinutes()).isEqualTo(45);
    }

    @Test
    void 探活与接口文档分组() throws Exception {
        MvcResult ping = mvc.perform(get("/api/v1/ping")).andReturn();
        assertThat(ping.getResponse().getStatus()).isEqualTo(200);
        assertThat(ping.getResponse().getHeader("X-Trace-Id")).hasSize(32);

        MvcResult user = mvc.perform(get("/v3/api-docs/user")).andReturn();
        assertThat(user.getResponse().getStatus()).isEqualTo(200);
        JsonNode doc = TestMvc.json(user.getResponse().getContentAsString());
        assertThat(doc.get("info").get("title").asString()).isEqualTo("智牧通服务端接口");
        assertThat(doc.get("paths").has("/api/v1/ping")).isTrue();
        assertThat(doc.get("components").get("securitySchemes").has("bearerAuth")).isTrue();

        MvcResult admin = mvc.perform(get("/v3/api-docs/admin")).andReturn();
        assertThat(admin.getResponse().getStatus()).isEqualTo(200);
    }
}
