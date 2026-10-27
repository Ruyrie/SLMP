package com.zhimu.server.biz.finance.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService.InboundExpense;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService.ReturnRefundIncome;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.service.impl.FinanceSourceWriteServiceImpl;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.common.error.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceSourceWriteServiceTest {
    private final FinanceRecordMapper records = mock(FinanceRecordMapper.class);
    private final FinanceCategoryMapper categories = mock(FinanceCategoryMapper.class);
    private final FinanceSourceWriteServiceImpl writer = new FinanceSourceWriteServiceImpl(records, categories);
    private final AtomicReference<FinanceRecord> saved = new AtomicReference<>();

    @BeforeAll static void tables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "finance-source-test");
        TableInfoHelper.initTableInfo(assistant, FinanceRecord.class);
        TableInfoHelper.initTableInfo(assistant, FinanceCategory.class);
    }

    @BeforeEach void persistence() {
        when(records.selectOne(any())).thenAnswer(call -> saved.get());
        when(records.insert(any(FinanceRecord.class))).thenAnswer(call -> {
            FinanceRecord record = call.getArgument(0); record.setId(88001L); saved.set(record); return 1;
        });
        when(records.update(any(FinanceRecord.class), any())).thenReturn(1);
        FinanceCategory feed = new FinanceCategory(); feed.setId(101L);
        when(categories.selectOne(any())).thenReturn(feed);
    }

    private InboundExpense source(String type, String amount, boolean active) {
        return new InboundExpense(1001L, type, 6201L, "RK26106201", new BigDecimal(amount),
                LocalDate.of(2026, 10, 26), "入库物资 12 千克", active, 10001L);
    }

    @Test void feedCreatesOneAutomaticExpenseAndRetryReusesIt() {
        InboundExpense input = source(FinanceConst.SOURCE_FEED, "42", true);
        assertThat(writer.syncInboundExpense(input)).isEqualTo(88001L);
        assertThat(writer.syncInboundExpense(input)).isEqualTo(88001L);
        FinanceRecord record = saved.get();
        assertThat(record.getRecordType()).isEqualTo("EXPENSE");
        assertThat(record.getSourceType()).isEqualTo("FEED_INBOUND");
        assertThat(record.getSourceId()).isEqualTo(6201L);
        assertThat(record.getSourceNo()).isEqualTo("RK26106201");
        assertThat(record.getCategoryId()).isEqualTo(101L);
        assertThat(record.getAmount()).isEqualByComparingTo("42.00");
        assertThat(record.getBatchId()).isNull(); // 采购只记现金支出；饲喂领用才计入批次成本。
        assertThat(record.getCreatedBy()).isZero();
        verify(records, times(1)).insert(any(FinanceRecord.class));
        verify(records, never()).update(any(FinanceRecord.class), any());
    }

    @Test void drugUsesItsAutomaticExpenseSourceAndCategory() {
        FinanceCategory drug = new FinanceCategory(); drug.setId(102L);
        when(categories.selectOne(any())).thenReturn(drug);
        writer.syncInboundExpense(source(FinanceConst.SOURCE_DRUG, "96.50", true));
        assertThat(saved.get().getSourceType()).isEqualTo("DRUG_INBOUND");
        assertThat(saved.get().getCategoryId()).isEqualTo(102L);
        assertThat(saved.get().getAmount()).isEqualByComparingTo("96.50");
    }

    @Test void disabledSyncDoesNotGenerateExpense() {
        assertThat(writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "42", false))).isNull();
        verify(records, never()).insert(any(FinanceRecord.class));
        verifyNoInteractions(categories);
    }

    @Test void sourceCorrectionDisableAndReenableKeepTheSameAccountingRecord() {
        writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "42", true));
        writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "70", true));
        assertThat(saved.get().getAmount()).isEqualByComparingTo("70");
        assertThat(writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "70", false))).isEqualTo(88001L);
        assertThat(saved.get().getStatus()).isEqualTo("VOID");
        writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "70", true));
        assertThat(saved.get().getStatus()).isEqualTo("NORMAL");
        verify(records, times(1)).insert(any(FinanceRecord.class));
        verify(records, times(3)).update(any(FinanceRecord.class), any());
    }

    @Test void missingAutomaticCategoryFailsInsteadOfSilentlySkippingAccounting() {
        when(categories.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> writer.syncInboundExpense(source(FinanceConst.SOURCE_FEED, "42", true)))
                .isInstanceOf(BizException.class).hasMessageContaining("记账分类");
        verify(records, never()).insert(any(FinanceRecord.class));
    }

    private ReturnRefundIncome refund(String amount, Long financeId) {
        return new ReturnRefundIncome(1001L, 6501L, "CK26106501", new BigDecimal(amount),
                LocalDate.of(2026, 10, 27), "退货实际到账", 10001L, financeId);
    }

    @Test void returnRefundCreatesIncomeWithOriginalOutboundTargetAndCorrectsSameRow() {
        when(records.updateById(any(FinanceRecord.class))).thenReturn(1);
        assertThat(writer.syncReturnRefundIncome(refund("80.25", null))).isEqualTo(88001L);
        FinanceRecord record = saved.get();
        assertThat(record.getRecordType()).isEqualTo("INCOME");
        assertThat(record.getSourceType()).isEqualTo("DRUG_RETURN_REFUND");
        assertThat(record.getSourceId()).isEqualTo(6501L);
        assertThat(record.getSourceNo()).isEqualTo("CK26106501");
        assertThat(record.getAmount()).isEqualByComparingTo("80.25");
        assertThat(FinanceConst.sourceTypeApp(record.getSourceType())).isEqualTo("DRUG_RETURN_REFUND");
        assertThat(FinanceConst.sourceLabel(record.getSourceType())).isEqualTo("来自药苗退货退款");
        assertThat(FinanceConst.sourceTargetBizType(record.getSourceType())).isEqualTo("OUTBOUND");
        assertThat(writer.syncReturnRefundIncome(refund("75.50", 88001L))).isEqualTo(88001L);
        assertThat(saved.get().getAmount()).isEqualByComparingTo("75.50");
        assertThat(saved.get().getBatchId()).isNull();
        verify(records, times(1)).insert(any(FinanceRecord.class));
        verify(records, times(1)).updateById(any(FinanceRecord.class));
    }

    @Test void secondRefundSourceCannotCreateDuplicateOrResurrectVoidedIncome() {
        writer.syncReturnRefundIncome(refund("80", null));
        assertThatThrownBy(() -> writer.syncReturnRefundIncome(refund("80", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("已有关联");
        assertThatThrownBy(() -> writer.syncReturnRefundIncome(refund("80", 99001L)))
                .isInstanceOf(BizException.class).hasMessageContaining("状态异常");
        saved.get().setStatus("VOID");
        assertThatThrownBy(() -> writer.syncReturnRefundIncome(refund("80", 88001L)))
                .isInstanceOf(BizException.class).hasMessageContaining("状态异常");
        verify(records, times(1)).insert(any(FinanceRecord.class));
        verify(records, never()).updateById(any(FinanceRecord.class));
    }

    @Test void missingRefundCategoryFailsInsteadOfCreatingUnclassifiedIncome() {
        when(categories.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> writer.syncReturnRefundIncome(refund("80", null)))
                .isInstanceOf(BizException.class).hasMessageContaining("退款自动记账分类");
        verify(records, never()).insert(any(FinanceRecord.class));
    }
}
