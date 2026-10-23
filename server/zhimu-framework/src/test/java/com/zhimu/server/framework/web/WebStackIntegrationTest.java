package com.zhimu.server.framework.web;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.exception.GlobalExceptionHandler;
import com.zhimu.server.framework.jackson.JacksonConfig;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.AdminAuthInterceptor;
import com.zhimu.server.framework.security.interceptor.AuthInterceptor;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import com.zhimu.server.framework.security.interceptor.OriginCheckInterceptor;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import com.zhimu.server.framework.security.session.AdminSessionService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 按生产方式装配（Jackson 定制器、WebMvcConfig、TraceFilter、拦截器、全局异常处理）的切片测试。
 */
@WebMvcTest
@ContextConfiguration(classes = WebStackIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "zhimu.security.jwt-user-secret=unit-test-only-secret-0123456789-abcdef",
        "zhimu.app-version.min-version=2.0.0",
        "zhimu.app-version.download-url=https://example.com/app.apk"})
class WebStackIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @Import({JacksonConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, TraceFilter.class,
            UserJwtService.class, AuthInterceptor.class, AdminAuthInterceptor.class, FarmContextInterceptor.class,
            OriginCheckInterceptor.class, SystemController.class, DemoController.class})
    static class TestConfig {
    }

    @Getter
    @Setter
    public static class DemoQuery extends PageQuery {
        private LocalDate startDate;
        private LocalDateTime since;
        private String bizType;
        private List<Long> categoryIds;
    }

    @Getter
    @Setter
    public static class DemoVO {
        private Boolean isBreeding = true;
        private Integer recent7DoneDays = 6;
        @ManagerView
        private BigDecimal unitPrice = new BigDecimal("8.80");
        private String remark;
        private DemoQuery query;
    }

    @RestController
    public static class DemoController {

        @RequireFarm
        @GetMapping("/api/v1/demo")
        public ApiResponse<DemoVO> demo(@Valid DemoQuery query) {
            DemoVO vo = new DemoVO();
            vo.setQuery(query);
            return ApiResponse.ok(vo);
        }
    }

    @MockitoBean
    private AuthStateCache authStateCache;
    @MockitoBean
    private FarmMemberCache farmMemberCache;
    @MockitoBean
    private AdminSessionService adminSessionService;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserJwtService jwtService;

    private MvcResult perform(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return result;
    }

    @Test
    void 探活接口_trace_id与响应头一致_低版本App收到版本提示() throws Exception {
        MvcResult result = perform(get("/api/v1/ping").header("X-Client", "APP_ANDROID").header("X-App-Version", "1.0.0"), 200);

        JsonNode body = TestMvc.json(result.getResponse().getContentAsString());
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(body.get("code").asInt()).isZero();
        assertThat(body.get("message").asString()).isEqualTo("ok");
        assertThat(body.get("trace_id").asString()).isEqualTo(traceId);
        assertThat(body.get("data").get("trace_id").asString()).isEqualTo(traceId);
        assertThat(body.get("data").get("time").asString()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        assertThat(result.getResponse().getHeader("X-Min-App-Version")).isEqualTo("2.0.0");
        assertThat(result.getResponse().getHeader("X-App-Download-Url")).isEqualTo("https://example.com/app.apk");
    }

    @Test
    void 未登录访问返回401和JSON() throws Exception {
        MvcResult result = perform(get("/api/v1/demo"), 401);
        JsonNode body = TestMvc.json(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(40101);
        assertThat(body.get("trace_id").asString()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void 不存在的接口返回40401() throws Exception {
        JsonNode body = TestMvc.json(perform(get("/api/v1/not-exists"), 404).getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(40401);
    }

    @Test
    void 完整链路_登录_养殖场_snake_case参数_JSON命名_员工裁剪() throws Exception {
        String token = jwtService.issue(1L, ClientType.APP_ANDROID, 0).token();
        when(farmMemberCache.get(1001L, 1L)).thenReturn(
                new FarmContext.FarmMember(1001L, "东山猪场", 1L, 9L, FarmRole.STAFF, Set.of(), FarmStatus.NORMAL));

        MvcResult result = perform(get("/api/v1/demo?start_date=2026-10-19&biz_type=FEED&page=2"
                + "&since=2026-10-19 08:30&category_ids=3,4")
                .header("Authorization", "Bearer " + token).header("X-Farm-Id", "1001"), 200);

        JsonNode data = TestMvc.json(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("is_breeding").asBoolean()).isTrue();
        assertThat(data.get("recent7_done_days").asInt()).isEqualTo(6);
        assertThat(data.has("unit_price")).isFalse();
        assertThat(data.has("remark")).isFalse();
        assertThat(data.get("query").get("start_date").asString()).isEqualTo("2026-10-19");
        assertThat(data.get("query").get("biz_type").asString()).isEqualTo("FEED");
        assertThat(data.get("query").get("page").asInt()).isEqualTo(2);
        assertThat(data.get("query").get("since").asString()).isEqualTo("2026-10-19 08:30:00");
        assertThat(data.get("query").get("category_ids").toString()).isEqualTo("[3,4]");
    }
}
