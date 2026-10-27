package com.zhimu.server.biz.health.controller;

import com.zhimu.server.biz.health.HealthTestMvc;
import com.zhimu.server.biz.health.request.DisinfectRecordCreateRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordUpdateRequest;
import com.zhimu.server.biz.health.service.DisinfectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用与线上相同的 JSON 解析器验证 Web 登记和编辑请求，不能只模拟接口成功响应。 */
class DisinfectWebRequestContractTest {
    private final DisinfectService service = mock(DisinfectService.class);
    private MockMvc mvc;
    private static final String BODY = """
            {"record_type":"PLAN_TASK","task_id":7001,"pen_id":null,
             "actual_time":"2026-10-28 00:24:00","executor_id":99002,
             "drug_id":3001,"drug_lot_id":4001,"usage_qty":19.5,
             "concentration":"1:500","method_code":"SPRAY","file_ids":[81],"version":1}
            """;

    @BeforeEach
    void setUp() {
        mvc = HealthTestMvc.builder(new DisinfectController(service)).build();
    }

    @Test
    void registrationBindsBeijingTimeAndUploadedPhoto() throws Exception {
        mvc.perform(post("/api/v1/disinfect-records")
                        .header("Idempotency-Key", "web-disinfect-contract")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(DisinfectRecordCreateRequest.class);
        verify(service).createRecord(request.capture());
        assertThat(request.getValue().getActualTime()).isEqualTo(LocalDateTime.of(2026, 10, 28, 0, 24));
        assertThat(request.getValue().getUsageQty()).isEqualByComparingTo("19.5");
        assertThat(request.getValue().getFileIds()).containsExactly(81L);
    }

    @Test
    void editBindsBeijingTimeAndUploadedPhoto() throws Exception {
        mvc.perform(put("/api/v1/disinfect-records/9001")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(DisinfectRecordUpdateRequest.class);
        verify(service).updateRecord(org.mockito.ArgumentMatchers.eq(9001L), request.capture());
        assertThat(request.getValue().getActualTime()).isEqualTo(LocalDateTime.of(2026, 10, 28, 0, 24));
        assertThat(request.getValue().getFileIds()).containsExactly(81L);
    }
}
