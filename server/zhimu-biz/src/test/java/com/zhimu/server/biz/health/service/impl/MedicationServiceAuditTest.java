package com.zhimu.server.biz.health.service.impl;

import com.zhimu.server.admin.base.api.DrugCatalogQueryService;
import com.zhimu.server.admin.base.entity.DrugCatalogUsage;
import com.zhimu.server.biz.health.HealthTableInfo;
import com.zhimu.server.biz.health.entity.MedicationRecord;
import com.zhimu.server.biz.health.mapper.BatchWithdrawalMapper;
import com.zhimu.server.biz.health.mapper.HealthReferenceMapper;
import com.zhimu.server.biz.health.mapper.MedicationRecordMapper;
import com.zhimu.server.biz.health.query.FarmDrugRow;
import com.zhimu.server.biz.health.request.MedicationUpdateRequest;
import com.zhimu.server.biz.health.request.MedicationCreateRequest;
import com.zhimu.server.biz.health.support.HealthAudit;
import com.zhimu.server.biz.health.support.HealthSupport;
import com.zhimu.server.biz.herd.api.BatchInfo;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.api.DrugStockPort;
import com.zhimu.server.biz.stock.api.StockConsumeResult;
import com.zhimu.server.common.util.BeijingTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MedicationServiceAuditTest {
    private final MedicationRecordMapper records = mock(MedicationRecordMapper.class);
    private final HealthReferenceMapper references = mock(HealthReferenceMapper.class);
    private final HealthSupport support = mock(HealthSupport.class);
    private final HealthAudit audit = mock(HealthAudit.class);
    private final DrugStockPort stock = mock(DrugStockPort.class);
    private final DrugCatalogQueryService catalogs = mock(DrugCatalogQueryService.class);
    private MedicationServiceImpl service;
    private MedicationRecord record;

    @BeforeEach void setUp() {
        HealthTableInfo.ensure();
        BeijingTime.setClock(Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), BeijingTime.ZONE));
        service = new MedicationServiceImpl(records, mock(BatchWithdrawalMapper.class), references,
                support, audit, stock, catalogs, mock(BatchQueryService.class),
                mock(com.zhimu.server.biz.health.support.HealthManagerNotifier.class));
        when(support.farmId()).thenReturn(1001L);
        when(support.managerView()).thenReturn(true);
        when(support.userName(10001L)).thenReturn("王建国");
        when(support.batchNo(3001L)).thenReturn("ZM2603-015");
        when(support.requireBatch(3001L)).thenReturn(new BatchInfo(1001L, 3001L, "ZM2603-015",
                1L, 2L, BeijingTime.today().minusDays(200), 3200, "ACTIVE", List.of(2001L),
                1, 3200, 0, 0, 2001L));
        when(support.species(2L)).thenReturn("CHICKEN");
        FarmDrugRow drug = new FarmDrugRow();
        drug.setId(5001L);
        drug.setCatalogId(7001L);
        drug.setName("阿莫西林可溶性粉");
        drug.setUnit("G");
        drug.setCategory("VET_DRUG");
        when(references.selectDrugs(1001L)).thenReturn(List.of(drug));
        DrugCatalogUsage usage = new DrugCatalogUsage();
        usage.setAdminRoute("MIX_WATER");
        usage.setDoseUnit("G_PER_L_WATER");
        usage.setCourseDays(5);
        usage.setWithdrawalDays(7);
        usage.setApplicable(true);
        when(catalogs.usage(7001L, "CHICKEN")).thenReturn(usage);
        when(catalogs.usages(7001L)).thenReturn(List.of(usage));
        DrugLotOption lot = new DrugLotOption(6001L, "LOT001", BeijingTime.today().plusYears(1),
                365, "NORMAL", "正常", null, true, false, false, BigDecimal.ONE, 0);
        when(support.findLot(any(), eq(6001L))).thenReturn(lot);
        when(stock.deduct(any())).thenReturn(new StockConsumeResult(8001L, BigDecimal.ONE,
                new BigDecimal("555"), new BigDecimal("1000"), "LOT001", List.of()));
        record = new MedicationRecord();
        record.setId(5101L);
        record.setFarmId(1001L);
        record.setBatchId(3001L);
        record.setPenId(2001L);
        record.setCatalogId(7001L);
        record.setDrugId(5001L);
        record.setDrugName(drug.getName());
        record.setDrugLotId(6001L);
        record.setLotNo("LOT001");
        record.setDose(BigDecimal.ONE);
        record.setDoseUnit(usage.getDoseUnit());
        record.setRouteCode(usage.getAdminRoute());
        record.setUsedQty(new BigDecimal("500.000"));
        record.setUsedUnit("G");
        record.setFirstDate(BeijingTime.today());
        record.setLastDate(BeijingTime.today().plusDays(4));
        record.setCourseDays(5);
        record.setWithdrawalDays(7);
        record.setWithdrawalEndDate(BeijingTime.today().plusDays(11));
        record.setReason("原用药原因");
        record.setExecutorId(10001L);
        record.setCreatedBy(10001L);
        record.setCreatedAt(BeijingTime.now());
        record.setVersion(0);
        when(records.selectById(5101L)).thenReturn(record);
        when(records.selectEffective(1001L, 3001L)).thenReturn(List.of(record));
        when(records.updateById(any(MedicationRecord.class))).thenReturn(1);
    }

    @AfterEach void tearDown() { BeijingTime.setClock(null); }

    @Test void updateRecordsActualBeforeAndAfterValuesWithUnitsAndAllEditedFields() {
        MedicationUpdateRequest request = new MedicationUpdateRequest();
        request.setVersion(0);
        request.setUsedQty(new BigDecimal("555"));
        request.setCourseDays(6);
        request.setReason("更正用药原因");
        service.update(5101L, request);

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq(1001L), eq("MEDICATION_RECORD"), eq(5101L), eq("UPDATE"),
                eq("阿莫西林可溶性粉"), eq("ZM2603-015"), before.capture(), after.capture());
        assertThat(field(before.getValue(), "used_qty")).containsEntry("name", "实际用量").containsEntry("display", "500 克");
        assertThat(field(after.getValue(), "used_qty")).containsEntry("display", "555 克");
        assertThat(field(before.getValue(), "course_days")).containsEntry("display", "5 天");
        assertThat(field(after.getValue(), "course_days")).containsEntry("display", "6 天");
        assertThat(field(before.getValue(), "last_date")).containsEntry("display", "2026-10-31");
        assertThat(field(after.getValue(), "last_date")).containsEntry("display", "2026-11-01");
        assertThat(field(after.getValue(), "withdrawal_end_date")).containsEntry("display", "2026-11-08");
        assertThat(field(before.getValue(), "reason")).containsEntry("display", "原用药原因");
        assertThat(field(after.getValue(), "reason")).containsEntry("display", "更正用药原因");
        assertThat(field(after.getValue(), "route_code")).containsEntry("display", "混饮");
    }

    @Test void savingUnchangedValuesDoesNotCreateAPhantomModification() {
        MedicationUpdateRequest request = new MedicationUpdateRequest();
        request.setVersion(0);
        request.setUsedQty(new BigDecimal("500"));
        service.update(5101L, request);
        verifyNoInteractions(audit);
    }

    @Test void registrationStoresCompleteDisplaySnapshot() {
        BatchInfo batch = support.requireBatch(3001L);
        when(support.requireActiveBatch(3001L)).thenReturn(batch);
        when(support.mainPenId(any())).thenReturn(2001L);
        when(support.defaultExecutorId(2001L)).thenReturn(10001L);
        when(records.insert(any(MedicationRecord.class))).thenAnswer(invocation -> {
            MedicationRecord saved = invocation.getArgument(0);
            saved.setId(5102L);
            return 1;
        });
        MedicationCreateRequest request = new MedicationCreateRequest();
        request.setTargetType("BATCH");
        request.setBatchId(3001L);
        request.setDrugId(5001L);
        request.setDrugLotId(6001L);
        request.setDose(BigDecimal.ONE);
        request.setCourseDays(5);
        request.setUsedQty(new BigDecimal("555"));
        request.setFirstDate(BeijingTime.today());
        request.setReason("用药登记");
        service.create(request);

        ArgumentCaptor<Object> snapshot = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq(1001L), eq("MEDICATION_RECORD"), eq(5102L), eq("CREATE"),
                eq("阿莫西林可溶性粉"), eq("ZM2603-015"), isNull(), snapshot.capture());
        assertThat(field(snapshot.getValue(), "used_qty")).containsEntry("display", "555 克");
        assertThat(field(snapshot.getValue(), "first_date")).containsEntry("name", "首次用药日期")
                .containsEntry("display", "2026-10-27");
        assertThat(field(snapshot.getValue(), "executor_id")).containsEntry("display", "王建国");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(Object snapshot, String key) {
        return (Map<String, Object>) ((Map<?, ?>) snapshot).get(key);
    }
}
