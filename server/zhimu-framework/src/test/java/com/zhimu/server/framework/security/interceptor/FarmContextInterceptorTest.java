package com.zhimu.server.framework.security.interceptor;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.security.annotation.FarmRoles;
import com.zhimu.server.framework.security.annotation.RequireFarm;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.web.TestMvc;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class FarmContextInterceptorTest {

    private static final long USER_ID = 1L;

    @Getter
    @Setter
    public static class SaleVO {
        private BigDecimal qty = new BigDecimal("3");
        @ManagerView
        private BigDecimal unitPrice = new BigDecimal("12.50");
    }

    @RestController
    @RequestMapping("/api/v1")
    public static class TestController {

        @RequireFarm
        @GetMapping("/records")
        public ApiResponse<Map<String, Object>> list() {
            return ApiResponse.ok(current());
        }

        @RequireFarm
        @PostMapping("/records")
        public ApiResponse<Map<String, Object>> create() {
            return ApiResponse.ok(current());
        }

        @FarmRoles({FarmRole.OWNER, FarmRole.MANAGER})
        @GetMapping("/finance")
        public ApiResponse<Map<String, Object>> finance() {
            return ApiResponse.ok(current());
        }

        @RequireFarm(allowStatus = {FarmStatus.PENDING, FarmStatus.REJECTED})
        @PutMapping("/farms/{farmId}/registration")
        public ApiResponse<Map<String, Object>> resubmit(@PathVariable Long farmId) {
            return ApiResponse.ok(current());
        }

        @RequireFarm
        @GetMapping("/farms/{farmId}/members")
        public ApiResponse<Map<String, Object>> members(@PathVariable Long farmId) {
            return ApiResponse.ok(current());
        }

        @GetMapping("/plain")
        public ApiResponse<Map<String, Object>> plain() {
            return ApiResponse.ok(current());
        }

        @GetMapping("/sale")
        public ApiResponse<SaleVO> sale() {
            return ApiResponse.ok(new SaleVO());
        }

        private static Map<String, Object> current() {
            Map<String, Object> data = new LinkedHashMap<>();
            FarmContext.FarmMember member = FarmContext.get();
            data.put("farm_id", member == null ? 0 : member.farmId());
            data.put("role", member == null ? "" : member.role().name());
            return data;
        }
    }

    /** 代替 AuthInterceptor 写入当前用户 */
    static class FakeLogin implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            UserContext.set(new UserContext.LoginUser(USER_ID, ClientType.APP_ANDROID, 0, "j"));
            return true;
        }
    }

    private final FarmMemberCache cache = mock(FarmMemberCache.class);
    private final MockMvc mvc = TestMvc.builder(new TestController())
            .addMappedInterceptors(new String[]{"/api/v1/**"}, new FakeLogin(), new FarmContextInterceptor(cache))
            .build();

    private static FarmContext.FarmMember member(long farmId, FarmRole role, FarmStatus status) {
        return new FarmContext.FarmMember(farmId, "东山猪场", USER_ID, 9L, role, Set.of(), status);
    }

    private JsonNode perform(RequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return TestMvc.json(result.getResponse().getContentAsString());
    }

    @Test
    void 没有养殖场ID或不是成员返回40302() throws Exception {
        assertThat(perform(get("/api/v1/records"), 403).get("code").asInt()).isEqualTo(40302);

        when(cache.get(1001L, USER_ID)).thenReturn(null);
        assertThat(perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 403).get("code").asInt()).isEqualTo(40302);
        assertThat(perform(get("/api/v1/records").header("X-Farm-Id", "abc"), 403).get("code").asInt()).isEqualTo(40302);
    }

    @Test
    void 成员通过后写入FarmContext() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.STAFF, FarmStatus.NORMAL));
        JsonNode data = perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 200).get("data");
        assertThat(data.get("farm_id").asLong()).isEqualTo(1001L);
        assertThat(data.get("role").asString()).isEqualTo("STAFF");
        assertThat(FarmContext.get()).isNull();
    }

    @Test
    void 状态不允许返回40305_文案按状态区分() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.OWNER, FarmStatus.PENDING));
        JsonNode pending = perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 403);
        assertThat(pending.get("code").asInt()).isEqualTo(40305);
        assertThat(pending.get("message").asString()).isEqualTo("养殖场正在审核中，审核通过后才能使用");

        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.OWNER, FarmStatus.REJECTED));
        assertThat(perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 403).get("message").asString())
                .isEqualTo("养殖场审核未通过，请修改后重新提交");

        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.OWNER, FarmStatus.DISABLED));
        perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 200);
        JsonNode disabledWrite = perform(post("/api/v1/records").header("X-Farm-Id", "1001"), 403);
        assertThat(disabledWrite.get("code").asInt()).isEqualTo(40305);
        assertThat(disabledWrite.get("message").asString()).isEqualTo("养殖场已停用，重新启用后才能登记");
    }

    @Test
    void 冻结返回40306带养殖场名() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.OWNER, FarmStatus.FROZEN));
        JsonNode body = perform(get("/api/v1/records").header("X-Farm-Id", "1001"), 403);
        assertThat(body.get("code").asInt()).isEqualTo(40306);
        assertThat(body.get("message").asString()).isEqualTo("东山猪场已被平台冻结，暂时不能使用。原因见消息中心");
    }

    @Test
    void 角色不符返回40303() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.STAFF, FarmStatus.NORMAL));
        assertThat(perform(get("/api/v1/finance").header("X-Farm-Id", "1001"), 403).get("code").asInt()).isEqualTo(40303);

        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.MANAGER, FarmStatus.NORMAL));
        assertThat(perform(get("/api/v1/finance").header("X-Farm-Id", "1001"), 200).get("data").get("role").asString())
                .isEqualTo("MANAGER");
    }

    @Test
    void 路径中的farmId优先于请求头_allowStatus覆盖默认规则() throws Exception {
        when(cache.get(2002L, USER_ID)).thenReturn(member(2002L, FarmRole.OWNER, FarmStatus.PENDING));

        JsonNode data = perform(put("/api/v1/farms/2002/registration").header("X-Farm-Id", "1001"), 200).get("data");
        assertThat(data.get("farm_id").asLong()).isEqualTo(2002L);
        verify(cache, never()).get(eq(1001L), anyLong());

        JsonNode members = perform(get("/api/v1/farms/2002/members").header("X-Farm-Id", "1001"), 403);
        assertThat(members.get("code").asInt()).isEqualTo(40305);
    }

    @Test
    void 没有注解但带X_Farm_Id时尽力写入上下文_失败不报错() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.STAFF, FarmStatus.FROZEN));
        assertThat(perform(get("/api/v1/plain").header("X-Farm-Id", "1001"), 200).get("data").get("farm_id").asLong())
                .isEqualTo(1001L);

        when(cache.get(1001L, USER_ID)).thenThrow(new IllegalStateException("redis down"));
        assertThat(perform(get("/api/v1/plain").header("X-Farm-Id", "1001"), 200).get("data").get("farm_id").asLong())
                .isZero();
        assertThat(perform(get("/api/v1/plain"), 200).get("data").get("farm_id").asLong()).isZero();
    }

    @Test
    void 员工请求时管理视图字段被裁剪() throws Exception {
        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.STAFF, FarmStatus.NORMAL));
        JsonNode staff = perform(get("/api/v1/sale").header("X-Farm-Id", "1001"), 200).get("data");
        assertThat(staff.has("unit_price")).isFalse();
        assertThat(staff.get("qty").asInt()).isEqualTo(3);

        when(cache.get(1001L, USER_ID)).thenReturn(member(1001L, FarmRole.OWNER, FarmStatus.NORMAL));
        JsonNode owner = perform(get("/api/v1/sale").header("X-Farm-Id", "1001"), 200).get("data");
        assertThat(owner.get("unit_price").decimalValue()).isEqualByComparingTo("12.50");
    }
}
