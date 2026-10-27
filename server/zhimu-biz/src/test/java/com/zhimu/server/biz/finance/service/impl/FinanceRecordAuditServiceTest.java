package com.zhimu.server.biz.finance.service.impl;

import com.zhimu.server.biz.account.api.AccountQueryService;
import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.OperationLogMapper;
import com.zhimu.server.biz.common.query.RecordLogRow;
import com.zhimu.server.biz.common.service.impl.OperationLogRecorderImpl;
import com.zhimu.server.biz.common.service.impl.OperationLogServiceImpl;
import com.zhimu.server.biz.common.vo.RecordLogVO;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.finance.FinanceTestTables;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.mapper.*;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.MarketPriceService;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.herd.api.BatchQueryService;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import com.zhimu.server.integration.ipregion.IpRegionResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceRecordAuditServiceTest {
    @Mock private FinanceRecordMapper recordMapper;
    @Mock private FinanceCategoryMapper categoryMapper;
    @Mock private ExpenseSubcategoryMapper subcategoryMapper;
    @Mock private SaleOrderMapper saleOrderMapper;
    @Mock private SaleReceiptMapper saleReceiptMapper;
    @Mock private BatchSettlementMapper settlementMapper;
    @Mock private BatchSettlementSupplementMapper supplementMapper;
    @Mock private FinanceSupport support;
    @Mock private MarketPriceService marketPriceService;
    @Mock private FarmQueryService farmQueryService;
    @Mock private BizMediaService bizMediaService;
    @Mock private BatchQueryService batchQueryService;
    @Mock private FileMapper fileMapper;
    @Mock private OperationLogRecorder operationLogRecorder;
    @InjectMocks private FinanceRecordServiceImpl service;

    private FinanceRecord record;

    @BeforeEach
    void setUp() {
        FinanceTestTables.init();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "qa-audit"));
        record = new FinanceRecord();
        record.setId(98001L);
        record.setFarmId(1001L);
        record.setRecordType("INCOME");
        record.setSourceType("MANUAL");
        record.setCategoryId(108L);
        record.setAmount(new BigDecimal("18.00"));
        record.setRecordDate(BeijingTime.today());
        record.setNote("初始记录");
        record.setStatus("NORMAL");
        record.setVoucherCount(0);
        record.setVersion(1);
        FinanceCategory category = new FinanceCategory();
        category.setId(108L);
        category.setName("其他收入");
        category.setDirection("INCOME");
        category.setStatus("ENABLED");
        when(support.farmId()).thenReturn(1001L);
        when(recordMapper.selectById(98001L)).thenReturn(record);
        lenient().when(recordMapper.updateById(any(FinanceRecord.class))).thenReturn(1);
        lenient().when(categoryMapper.selectById(108L)).thenReturn(category);
        lenient().when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void successfulManualUpdateWritesChangedFieldsReadableByU14() {
        FinanceRequests.FinanceRecordRequest request = request("25.50", "QA 调整");
        request.setRecordDate(BeijingTime.today().minusDays(1).toString());
        service.update(98001L, request);

        OperationLogEntry entry = capturedEntry();
        assertThat(entry.getBizType()).isEqualTo("FINANCE_RECORD");
        assertThat(entry.getBizId()).isEqualTo(98001L);
        assertThat(entry.getFarmId()).isEqualTo(1001L);
        assertThat(entry.getOpAction()).isEqualTo("UPDATE");

        RecordLogVO log = readThroughRealRecorderAndQuery(entry);
        assertThat(log.getOpActionText()).isEqualTo("修改");
        assertThat(log.getChanges()).hasSize(3);
        assertThat(log.getChanges()).anySatisfy(change -> {
            assertThat(change.getFieldName()).isEqualTo("金额");
            assertThat(change.getBefore()).isEqualTo("18.00 元");
            assertThat(change.getAfter()).isEqualTo("25.50 元");
        });
        assertThat(log.getChanges()).anySatisfy(change -> {
            assertThat(change.getFieldName()).isEqualTo("说明");
            assertThat(change.getBefore()).isEqualTo("初始记录");
            assertThat(change.getAfter()).isEqualTo("QA 调整");
        });
    }

    @Test
    void unchangedSaveDoesNotInventAModification() {
        service.update(98001L, request("18", "初始记录"));
        verifyNoInteractions(operationLogRecorder);
    }

    @Test
    void optimisticLockFailureDoesNotWriteSuccessfulAudit() {
        when(recordMapper.updateById(any(FinanceRecord.class))).thenReturn(0);
        assertThatThrownBy(() -> service.update(98001L, request("25.50", "QA 调整")))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(operationLogRecorder);
    }

    @Test
    void automaticRefundCannotBeRecreatedByManualCategoryOrEditedOrVoidedDirectly() {
        FinanceCategory category = new FinanceCategory();
        category.setId(108L); category.setName("药苗退货退款"); category.setDirection("INCOME");
        category.setStatus("ENABLED"); category.setAutoSource("DRUG_RETURN_REFUND");
        when(categoryMapper.selectById(108L)).thenReturn(category);
        assertThatThrownBy(() -> service.create(request("80", "实际到账"), null))
                .isInstanceOf(BizException.class).hasMessageContaining("不能重复手工记账");
        record.setSourceType("DRUG_RETURN_REFUND");
        assertThatThrownBy(() -> service.update(98001L, request("75", "更正")))
                .isInstanceOf(BizException.class).hasMessageContaining("自动生成");
        assertThatThrownBy(() -> service.voidRecord(98001L, 1))
                .isInstanceOf(BizException.class).hasMessageContaining("自动生成");
        verifyNoInteractions(operationLogRecorder);
    }

    @Test
    void voidingManualRecordWritesTheStatusTransition() {
        service.voidRecord(98001L, 1);
        OperationLogEntry entry = capturedEntry();
        assertThat(entry.getOpAction()).isEqualTo("VOID");
        RecordLogVO log = readThroughRealRecorderAndQuery(entry);
        assertThat(log.getOpActionText()).isEqualTo("作废");
        assertThat(log.getChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFieldName()).isEqualTo("状态");
            assertThat(change.getAfter()).isEqualTo("已作废");
        });
    }

    private OperationLogEntry capturedEntry() {
        ArgumentCaptor<OperationLogEntry> entry = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(operationLogRecorder).record(entry.capture());
        return entry.getValue();
    }

    @SuppressWarnings("unchecked")
    private RecordLogVO readThroughRealRecorderAndQuery(OperationLogEntry entry) {
        OperationLogMapper logs = mock(OperationLogMapper.class);
        AccountQueryService accounts = mock(AccountQueryService.class);
        ObjectProvider<AccountQueryService> accountProvider = mock(ObjectProvider.class);
        when(accountProvider.getIfAvailable()).thenReturn(accounts);
        ObjectProvider<IpRegionResolver> regions = mock(ObjectProvider.class);
        OperationLogRecorderImpl recorder = new OperationLogRecorderImpl(logs, accountProvider, regions);
        recorder.record(entry);
        ArgumentCaptor<OperationLog> stored = ArgumentCaptor.forClass(OperationLog.class);
        verify(logs).insert(stored.capture());
        OperationLog persisted = stored.getValue();
        RecordLogRow row = new RecordLogRow();
        row.setId(1L);
        row.setFarmId(persisted.getFarmId());
        row.setOperatorId(persisted.getOperatorId());
        row.setOpAction(persisted.getOpAction());
        row.setBeforeJson(persisted.getBeforeJson());
        row.setAfterJson(persisted.getAfterJson());
        when(logs.selectUserLogs("FINANCE_RECORD", 98001L, 200)).thenReturn(List.of(row));
        FarmMemberCache members = mock(FarmMemberCache.class);
        when(members.get(1001L, 10001L)).thenReturn(new FarmContext.FarmMember(1001L, "QA 场", 10001L,
                5001L, FarmRole.OWNER, Set.of(), FarmStatus.NORMAL));
        OperationLogServiceImpl query = new OperationLogServiceImpl(logs, accountProvider, members);
        return query.listByBiz(BizType.FINANCE_RECORD, 98001L, 200).get(0);
    }

    private FinanceRequests.FinanceRecordRequest request(String amount, String note) {
        FinanceRequests.FinanceRecordRequest request = new FinanceRequests.FinanceRecordRequest();
        request.setRecordType("INCOME");
        request.setCategoryId(108L);
        request.setAmount(new BigDecimal(amount));
        request.setRecordDate(BeijingTime.today().toString());
        request.setNote(note);
        request.setVersion(1);
        return request;
    }
}
