package com.zhimu.server.admin.system.interceptor;

import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AdminClientInterceptorTest {

    @RestController
    static class TestController {
        @GetMapping("/admin/v1/ping")
        ApiResponse<Map<String, Object>> ping() {
            return ApiResponse.ok(Map.of("pong", true));
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new TestController())
                .addMappedInterceptors(new String[]{"/admin/v1/**"}, new AdminClientInterceptor())
                .build();
    }

    @Test
    void 缺少X_Client返回40303() throws Exception {
        var response = mvc.perform(get("/admin/v1/ping")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40303);
        assertThat(TestMvc.json(response.getContentAsString()).get("message").asString())
                .isEqualTo("请求来源不正确");
    }

    @Test
    void X_Client不是ADMIN_WEB返回40303() throws Exception {
        var response = mvc.perform(get("/admin/v1/ping").header("X-Client", "APP_ANDROID"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TestMvc.json(response.getContentAsString()).get("code").asInt()).isEqualTo(40303);
    }

    @Test
    void ADMIN_WEB放行() throws Exception {
        var response = mvc.perform(get("/admin/v1/ping").header("X-Client", "ADMIN_WEB"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TestMvc.json(response.getContentAsString()).get("data").get("pong").asBoolean()).isTrue();
    }

    @Test
    void X_Client不区分大小写() throws Exception {
        assertThat(mvc.perform(get("/admin/v1/ping").header("X-Client", "admin_web"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
