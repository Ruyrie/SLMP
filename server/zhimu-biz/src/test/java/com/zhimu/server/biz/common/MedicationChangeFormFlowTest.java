package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.controller.ChangeRequestController;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.health.support.change.HealthChangeSupport;
import com.zhimu.server.biz.health.support.change.MedicationRecordChangeHandler;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 实际用药 handler 的删除权限必须传至客户端，并可提交待审核申请。 */
class MedicationChangeFormFlowTest {
    @AfterEach void clearContext() { CommonFlowFixture.logout(); }

    @Test void exposesDeleteOnlyActionAndCreatesPendingApplicationWithoutDeletingMedication() throws Exception {
        var mapper = mock(MedicationRecordMapper.class);
        var stock = mock(DrugStockPort.class);
        MedicationRecord row = new MedicationRecord();
        row.setId(5101L); row.setFarmId(1001L); row.setBatchId(3001L); row.setPenId(2003L);
        row.setDrugName("阿莫西林"); row.setVersion(2); row.setDeleted(false);
        row.setCreatedAt(BeijingTime.now().minusDays(1));
        row.setFirstDate(BeijingTime.today().minusDays(2)); row.setLastDate(BeijingTime.today().minusDays(1));
        when(mapper.selectByIdIncludingDeleted(5101L)).thenReturn(row);
        var handler = new MedicationRecordChangeHandler(mapper, mock(BatchWithdrawalMapper.class), stock, mock(HealthChangeSupport.class));
        var fixture = new CommonFlowFixture(handler);
        CommonFlowFixture.login(10001L);
        fixture.grant(1001L, 10001L, FarmRole.OWNER);
        var mvc = TestMvc.builder(new ChangeRequestController(fixture.applicationService)).build();

        mvc.perform(get("/api/v1/change-requests/form").param("biz_type", "MEDICATION_RECORD").param("biz_id", "5101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fields").isEmpty())
                .andExpect(jsonPath("$.data.delete_allowed").value(true))
                .andExpect(jsonPath("$.data.void_allowed").value(false));
        mvc.perform(get("/api/v1/change-requests/form").param("biz_type", "FEED_RECORD").param("biz_id", "7001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.delete_allowed").value(false));
        mvc.perform(post("/api/v1/change-requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"biz_type":"MEDICATION_RECORD","biz_id":5101,"change_type":"DELETE",
                 "record_version":"2","reason":"登记有误，申请删除后重新登记"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.change_type").value("DELETE"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertThat(fixture.requests).hasSize(1);
        assertThat(fixture.requests.getFirst().getFieldCode()).isNull();
        verify(mapper, never()).deleteById(anyLong());
        verifyNoInteractions(stock);
    }
}
