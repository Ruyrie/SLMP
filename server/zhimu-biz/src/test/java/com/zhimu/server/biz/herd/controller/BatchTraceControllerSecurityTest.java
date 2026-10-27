package com.zhimu.server.biz.herd.controller;

import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.biz.herd.service.BatchService;
import com.zhimu.server.biz.herd.service.BatchTraceService;
import com.zhimu.server.biz.herd.vo.BatchTraceVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.framework.security.interceptor.AuthInterceptor;
import com.zhimu.server.framework.security.interceptor.FarmContextInterceptor;
import com.zhimu.server.framework.security.jwt.UserJwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BatchTraceControllerSecurityTest {
    private final BatchTraceService traces = mock(BatchTraceService.class);
    private final BatchService batches = mock(BatchService.class);
    private final UserJwtService jwt = mock(UserJwtService.class);
    private final FarmMemberCache members = mock(FarmMemberCache.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        FarmContext.clear();
        mvc = TestMvc.builder(new BatchTraceController(traces), new BatchController(batches))
                .addInterceptors(new AuthInterceptor(jwt, mock(AuthStateCache.class)), new FarmContextInterceptor(members))
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void anonymousTraceReturnsOnlyThePublicAllowlistAndIsNotCached() throws Exception {
        BatchTraceVO trace = new BatchTraceVO();
        trace.setBatchNo("ZM2610-031");
        trace.setFarmName("公开养殖场");
        BatchTraceVO.Event event = new BatchTraceVO.Event();
        event.setType("IMMUNE");
        event.setName("已登记疫苗");
        event.setQuantity(100);
        trace.setEvents(List.of(event));
        when(traces.read("k7Rm2xQ9pT")).thenReturn(trace);

        var result = mvc.perform(get("/api/v1/public/batch-traces/k7Rm2xQ9pT"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.batch_no").value("ZM2610-031"))
                .andReturn();
        var data = TestMvc.json(result.getResponse().getContentAsString()).path("data");
        Set<String> allowed = Set.of("batch_no", "farm_name", "category_name", "breed_name", "source", "status",
                "entry_date", "end_date", "entry_age_days", "entry_avg_weight", "updated_at", "events");
        assertThat(data.propertyNames()).allMatch(allowed::contains);
        Set<String> eventFields = Set.of("type", "date", "name", "quantity", "avg_weight", "withdrawal_end_date", "updated_at");
        assertThat(data.path("events").get(0).propertyNames()).allMatch(eventFields::contains);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("contact_phone", "contact_name", "address",
                "unit_price", "sale_amount", "finance", "cost_amount", "pen_id", "farm_id", "batch_id", "executor_id", "note");
        verifyNoInteractions(jwt, members, batches);
    }

    @Test
    void anonymousUsersCannotReadInternalArchiveOrChangeTraceSettings() throws Exception {
        mvc.perform(get("/api/v1/batches/3001")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/batches/3001/trace-settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":7}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(batches, traces);
    }

    @Test
    void staffCannotChangePublicationOrRotateTokens() throws Exception {
        login(FarmRole.STAFF);
        mvc.perform(put("/api/v1/batches/3001/trace-settings").header("Authorization", "Bearer user")
                        .header("X-Farm-Id", "1001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"rotate\":true,\"version\":7}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40303));
        verifyNoInteractions(traces);
    }

    @Test
    void managersCanChangePublicationThroughTheAuthenticatedEndpoint() throws Exception {
        login(FarmRole.MANAGER);
        when(traces.settings(eq(3001L), any())).thenReturn(new BatchTraceService.Settings(false, false, 8,
                "http://127.0.0.1:5173/s/b/k7Rm2xQ9pT"));
        mvc.perform(put("/api/v1/batches/3001/trace-settings").header("Authorization", "Bearer user")
                        .header("X-Farm-Id", "1001").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"version\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
    }

    private void login(FarmRole role) {
        when(jwt.parse("user")).thenReturn(new UserJwtService.UserClaims(10001L, 0, ClientType.WEB, "test"));
        when(members.get(1001L, 10001L)).thenReturn(new FarmContext.FarmMember(1001L, "测试场", 10001L,
                5001L, role, Set.of(), FarmStatus.NORMAL));
    }
}
