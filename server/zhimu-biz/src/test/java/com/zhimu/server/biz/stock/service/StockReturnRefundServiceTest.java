package com.zhimu.server.biz.stock.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimu.server.biz.common.entity.ChangeRequest;
import com.zhimu.server.biz.common.mapper.ChangeRequestMapper;
import com.zhimu.server.biz.finance.api.FinanceSourceWriteService;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.request.StockReturnRefundRequest;
import com.zhimu.server.biz.stock.support.StockActions;
import com.zhimu.server.biz.stock.support.StockLookups;
import com.zhimu.server.biz.stock.vo.StockOutboundVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockReturnRefundServiceTest {
    private final StockOutboundMapper outbounds = mock(StockOutboundMapper.class);
    private final ChangeRequestMapper requests = mock(ChangeRequestMapper.class);
    private final FinanceSourceWriteService finance = mock(FinanceSourceWriteService.class);
    private final StockLookups lookups = mock(StockLookups.class);
    private final StockReturnRefundService service = new StockReturnRefundService(outbounds, requests, finance, lookups);
    private StockOutbound outbound;

    @BeforeAll static void tables() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "refund-test"), StockOutbound.class);
    }
    @BeforeEach void setup() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        BeijingTime.setClock(Clock.fixed(LocalDateTime.of(2026, 10, 27, 10, 0).atZone(zone).toInstant(), zone));
        context(FarmRole.OWNER, FarmStatus.NORMAL);
        outbound = new StockOutbound();
        outbound.setId(1L); outbound.setFarmId(1001L); outbound.setOutboundNo("CK-QA");
        outbound.setMaterialType("DRUG"); outbound.setOutType("RETURN"); outbound.setStatus("NORMAL");
        outbound.setOutDate(BeijingTime.today().minusDays(1)); outbound.setVersion(0);
        outbound.setQty(new BigDecimal("6")); outbound.setAmount(new BigDecimal("90"));
        when(outbounds.selectForUpdate(1L, 1001L)).thenReturn(outbound);
        when(outbounds.updateById(any(StockOutbound.class))).thenReturn(1);
        when(outbounds.update(isNull(), any())).thenReturn(1);
        when(lookups.userId()).thenReturn(10001L);
        when(finance.syncReturnRefundIncome(any())).thenReturn(88001L);
    }
    @AfterEach void clear() { FarmContext.clear(); BeijingTime.setClock(null); }
    private void context(FarmRole role, FarmStatus status) {
        FarmContext.set(new FarmContext.FarmMember(1001L, "QA", 10001L, 10L, role, Set.of(), status));
    }
    private StockReturnRefundRequest request(String amount) {
        StockReturnRefundRequest request = new StockReturnRefundRequest();
        request.setAmount(new BigDecimal(amount)); request.setRefundDate(BeijingTime.today());
        request.setRemark("实际到账"); request.setVersion(0); return request;
    }
    private void alreadyRefunded() {
        outbound.setRefundAmount(new BigDecimal("80.00")); outbound.setRefundDate(BeijingTime.today());
        outbound.setRefundRemark("实际到账"); outbound.setFinanceRecordId(88001L);
        outbound.setRefundRegisteredAt(BeijingTime.now().minusHours(1)); outbound.setRefundRegisteredBy(10001L);
        outbound.setRefundRequestId("original-key");
    }
    @Test void actualCashAmountCreatesIncomeWithoutChangingInventoryCostOrQuantity() {
        service.register(1L, request("80.25"), "key-1");
        ArgumentCaptor<FinanceSourceWriteService.ReturnRefundIncome> captured = ArgumentCaptor.forClass(FinanceSourceWriteService.ReturnRefundIncome.class);
        verify(finance).syncReturnRefundIncome(captured.capture());
        assertThat(captured.getValue().amount()).isEqualByComparingTo("80.25");
        assertThat(captured.getValue().outboundId()).isEqualTo(1L);
        assertThat(captured.getValue().financeRecordId()).isNull();
        assertThat(outbound.getRefundAmount()).isEqualByComparingTo("80.25");
        assertThat(outbound.getFinanceRecordId()).isEqualTo(88001L);
        assertThat(outbound.getQty()).isEqualByComparingTo("6");
        assertThat(outbound.getAmount()).isEqualByComparingTo("90");
    }
    @Test void sameRequestReplaysButDifferentKeyOrPayloadCannotRefundTwice() {
        service.register(1L, request("80"), "key-1");
        outbound.setVersion(1);
        service.register(1L, request("80.00"), "key-1");
        assertThatThrownBy(() -> service.register(1L, request("81"), "key-1")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key-2")).isInstanceOf(BizException.class);
        verify(finance, times(1)).syncReturnRefundIncome(any());
        verify(outbounds, times(1)).updateById(any(StockOutbound.class));
    }
    @Test void pendingChangePreventsRefund() {
        when(requests.selectPending("OUTBOUND", 1L)).thenReturn(new ChangeRequest());
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).hasMessageContaining("待审核");
        verifyNoInteractions(finance);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.001", "10000000000"})
    void invalidAmountsNeverReachAccounting(String amount) {
        assertThatThrownBy(() -> service.register(1L, request(amount), "key")).isInstanceOf(BizException.class);
        verifyNoInteractions(finance);
    }
    @Test void badDatesRemarkAndMissingVersionAreRejected() {
        StockReturnRefundRequest request = request("80");
        request.setRefundDate(BeijingTime.today().plusDays(1));
        assertThatThrownBy(() -> service.register(1L, request, "key")).hasMessageContaining("到账日期");
        request.setRefundDate(outbound.getOutDate().minusDays(1));
        assertThatThrownBy(() -> service.register(1L, request, "key")).hasMessageContaining("到账日期");
        request.setRefundDate(BeijingTime.today()); request.setRemark("a".repeat(201));
        assertThatThrownBy(() -> service.register(1L, request, "key")).hasMessageContaining("200");
        request.setRemark(null); request.setVersion(null);
        assertThatThrownBy(() -> service.register(1L, request, "key")).isInstanceOf(BizException.class);
        verifyNoInteractions(finance);
    }
    @Test void useOtherOutVoidCrossFarmAndStaffAreRejected() {
        outbound.setOutType("USE");
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).hasMessageContaining("药苗退货");
        outbound.setOutType("OTHER_OUT"); outbound.setMaterialType("FEED"); outbound.setDestination("RETURN_SUPPLIER");
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).hasMessageContaining("药苗退货");
        outbound.setOutType("RETURN"); outbound.setMaterialType("DRUG"); outbound.setStatus("VOID");
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).hasMessageContaining("已作废");
        outbound.setStatus("NORMAL"); outbound.setFarmId(1002L);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        outbound.setFarmId(1001L); context(FarmRole.STAFF, FarmStatus.NORMAL);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        context(FarmRole.OWNER, FarmStatus.DISABLED);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        verifyNoInteractions(finance);
    }
    @Test void staleVersionAndReusedKeyFailBeforeAccounting() {
        outbound.setVersion(1);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        outbound.setVersion(0); when(outbounds.selectOne(any())).thenReturn(new StockOutbound());
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).hasMessageContaining("另一张");
        verifyNoInteractions(finance);
    }
    @Test void correctionUpdatesSameFinanceIdAndPreservesFirstRegistrationWindow() {
        alreadyRefunded(); var firstRegistered = outbound.getRefundRegisteredAt();
        StockReturnRefundRequest corrected = request("75.50"); corrected.setRemark(null);
        service.correct(1L, corrected, "edit-key");
        ArgumentCaptor<FinanceSourceWriteService.ReturnRefundIncome> captured = ArgumentCaptor.forClass(FinanceSourceWriteService.ReturnRefundIncome.class);
        verify(finance).syncReturnRefundIncome(captured.capture());
        assertThat(captured.getValue().financeRecordId()).isEqualTo(88001L);
        assertThat(outbound.getRefundAmount()).isEqualByComparingTo("75.50");
        assertThat(outbound.getRefundRegisteredAt()).isEqualTo(firstRegistered);
        assertThat(outbound.getRefundRemark()).isNull();
        assertThat(outbound.getAmount()).isEqualByComparingTo("90");
        verify(outbounds).update(isNull(), any());
    }
    @Test void identicalCorrectionIsNoOpEvenWithOldVersion() {
        alreadyRefunded(); outbound.setVersion(2);
        service.correct(1L, request("80"), "retry-key");
        verifyNoInteractions(finance); verify(outbounds, never()).update(any(), any());
    }
    @Test void nextDayCorrectionCannotExtendWindow() {
        alreadyRefunded(); outbound.setRefundRegisteredAt(BeijingTime.now().minusDays(1));
        assertThatThrownBy(() -> service.correct(1L, request("75"), "edit-key")).hasMessageContaining("登记当天");
        verifyNoInteractions(finance);
    }
    @Test void financeFailureDoesNotMarkOriginalAsRefundedOrWriteSuccessLog() {
        when(finance.syncReturnRefundIncome(any())).thenThrow(new BizException(ErrorCode.SERVER_ERROR));
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        assertThat(outbound.getFinanceRecordId()).isNull();
        verify(outbounds, never()).updateById(any(StockOutbound.class));
        verify(lookups, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void originalUpdateFailurePropagatesForTransactionRollbackAndDoesNotAuditSuccess() {
        when(outbounds.updateById(any(StockOutbound.class))).thenReturn(0);
        assertThatThrownBy(() -> service.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        verify(lookups, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void postAccountingFailureRollsBackTheActualSpringTransactionBoundary() {
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = mock(org.springframework.transaction.TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional = (StockReturnRefundService) factory.getProxy();
        when(outbounds.updateById(any(StockOutbound.class))).thenReturn(0);
        assertThatThrownBy(() -> transactional.register(1L, request("80"), "key")).isInstanceOf(BizException.class);
        verify(finance).syncReturnRefundIncome(any());
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }
    @Test void refundedActionsFreezeInventoryButAllowSameDayRefundCorrection() {
        alreadyRefunded(); StockOutboundVO vo = new StockOutboundVO(); vo.setActions(StockActions.editable(BeijingTime.today()));
        service.populate(outbound, vo);
        assertThat(vo.getRefundStatus()).isEqualTo("REFUNDED");
        assertThat(vo.getActions().isCanEdit()).isFalse();
        assertThat(vo.getActions().getCanVoid()).isFalse();
        assertThat(vo.getActions().getCanRequestChange()).isFalse();
        assertThat(vo.getActions().getCanRefund()).isFalse();
        assertThat(vo.getActions().getCanEditRefund()).isTrue();
    }
}
