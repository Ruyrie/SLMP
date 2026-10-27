package com.zhimu.slmp.stock.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.request.ReturnRefundRequest;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.ui.refund.ReturnRefundViewModel;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ReturnRefundViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();
    private final StockRepository repository = mock(StockRepository.class);
    private final FarmContext farm = mock(FarmContext.class);

    private ReturnRefundViewModel model(SavedStateHandle handle) {
        when(farm.currentFarmId()).thenReturn(100L);
        ReturnRefundViewModel vm = new ReturnRefundViewModel(repository, farm);
        vm.attachSavedState(handle);
        vm.bind(record(false));
        return vm;
    }
    private static StockOutboundVO record(boolean refunded) {
        StockOutboundVO value = new StockOutboundVO();
        value.outboundId = 74; value.version = 1; value.outboundNo = "TH-74"; value.materialName = "阿莫西林";
        value.materialType = "DRUG"; value.outType = "RETURN"; value.outDate = "2026-10-18";
        value.amount = new BigDecimal("90.00"); value.actions = new StockActionsVO();
        value.refundStatus = refunded ? "REFUNDED" : "UNREFUNDED";
        value.actions.canRefund = !refunded; value.actions.canEditRefund = refunded;
        if (refunded) {
            value.refundAmount = new BigDecimal("100.00"); value.refundDate = "2026-10-19";
            value.financeRecordId = 999L;
        }
        return value;
    }

    @Test public void creationStartsBlankAndUsesActualAmountRatherThanInventoryCost() {
        ReturnRefundViewModel vm = model(new SavedStateHandle());
        assertTrue(vm.open(false));
        assertEquals("", vm.amountText.getValue());
        assertEquals("2026-10-19", vm.refundDate.getValue());
        when(repository.registerReturnRefund(eq(74L), any(), anyString())).thenReturn(Single.just(record(true)));
        vm.amountText.setValue("100.00"); vm.submit();
        ArgumentCaptor<ReturnRefundRequest> request = ArgumentCaptor.forClass(ReturnRefundRequest.class);
        verify(repository).registerReturnRefund(eq(74L), request.capture(), anyString());
        assertEquals(new BigDecimal("100.00"), request.getValue().amount);
        assertEquals(Integer.valueOf(1), request.getValue().version);
        assertEquals("REFUNDED", vm.getRecord().getValue().refundStatus);
        assertFalse(ReturnRefundViewModel.canRegister(vm.getRecord().getValue()));
        verify(repository, never()).createDrugOutbound(any(), anyString());
        verify(repository, never()).updateOutbound(anyLong(), any());
    }

    @Test public void serverRefundFieldsAndPermissionsDeserializeFromTheFlatDetailContract() {
        StockOutboundVO value = com.zhimu.slmp.network.GsonFactory.get().fromJson("{\"outbound_id\":74,\"material_type\":\"DRUG\","
                + "\"out_type\":\"RETURN\",\"refund_status\":\"REFUNDED\",\"refund_amount\":99.50,\"refund_date\":\"2026-10-19\","
                + "\"refund_remark\":\"已到账\",\"finance_record_id\":999,\"actions\":{\"can_refund\":false,\"can_edit_refund\":true}}", StockOutboundVO.class);
        assertEquals(new BigDecimal("99.50"), value.refundAmount);
        assertEquals(Long.valueOf(999L), value.financeRecordId);
        assertFalse(ReturnRefundViewModel.canRegister(value));
        assertTrue(ReturnRefundViewModel.canCorrect(value));
    }

    @Test public void duplicateClicksAreBlockedWhileSubmittingAndAfterCompletion() {
        ReturnRefundViewModel vm = model(new SavedStateHandle()); vm.open(false); vm.amountText.setValue("100");
        SingleSubject<StockOutboundVO> pending = SingleSubject.create();
        when(repository.registerReturnRefund(eq(74L), any(), anyString())).thenReturn(pending);
        vm.submit(); vm.submit();
        assertTrue(vm.getSubmitting().getValue());
        verify(repository, times(1)).registerReturnRefund(eq(74L), any(), anyString());
        pending.onSuccess(record(true)); vm.submit();
        verify(repository, times(1)).registerReturnRefund(eq(74L), any(), anyString());
    }

    @Test public void timeoutRetryAndRecreationRetainTheSamePayloadAndIdempotencyKey() {
        SavedStateHandle handle = new SavedStateHandle();
        ReturnRefundViewModel vm = model(handle); vm.open(false); vm.amountText.setValue("100");
        when(repository.registerReturnRefund(eq(74L), any(), anyString())).thenReturn(Single.error(new SocketTimeoutException("timeout")));
        vm.submit(); vm.submit();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : handle.keys()) snapshot.put(key, handle.get(key));
        ReturnRefundViewModel recreated = model(new SavedStateHandle(snapshot));
        assertTrue(recreated.open(false)); assertEquals("100", recreated.amountText.getValue()); recreated.submit();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, times(3)).registerReturnRefund(eq(74L), any(), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals(keys.getAllValues().get(1), keys.getAllValues().get(2));
        assertFalse(recreated.getErrors().getValue().isEmpty());
    }

    @Test public void refetchedSuccessfulTimeoutResultClosesThePendingRequestWithoutAnotherPost() {
        ReturnRefundViewModel vm = model(new SavedStateHandle()); vm.open(false); vm.amountText.setValue("100");
        when(repository.registerReturnRefund(eq(74L), any(), anyString())).thenReturn(Single.error(new SocketTimeoutException()));
        vm.submit(); vm.bind(record(true));
        assertEquals("REFUNDED", vm.getRecord().getValue().refundStatus);
        assertTrue(vm.getErrors().getValue().isEmpty());
        assertNull(vm.getSavedState().get("idem_key"));
        verify(repository, times(1)).registerReturnRefund(eq(74L), any(), anyString());
    }

    @Test public void restoredPendingSubmissionRecognizesTheServerSuccessAfterProcessRecreation() {
        SavedStateHandle handle = new SavedStateHandle();
        ReturnRefundViewModel vm = model(handle); vm.open(false); vm.amountText.setValue("100");
        when(repository.registerReturnRefund(eq(74L), any(), anyString())).thenReturn(Single.error(new SocketTimeoutException()));
        vm.submit();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String key : handle.keys()) snapshot.put(key, handle.get(key));
        ReturnRefundViewModel restored = model(new SavedStateHandle(snapshot));
        restored.bind(record(true));
        assertNull(restored.getSavedState().get("idem_key"));
        assertTrue(ReturnRefundViewModel.isRefunded(restored.getRecord().getValue()));
        verify(repository, times(1)).registerReturnRefund(eq(74L), any(), anyString());
    }

    @Test public void moneyDateAndRemarkValidationPreventsAnyRequest() {
        ReturnRefundViewModel vm = model(new SavedStateHandle()); vm.open(false);
        vm.amountText.setValue("100.001"); vm.refundDate.setValue("2026-10-17"); vm.remark.setValue(new String(new char[201]).replace('\0', '字'));
        vm.submit();
        assertTrue(vm.getErrors().getValue().containsKey("amount"));
        assertTrue(vm.getErrors().getValue().containsKey("refund_date"));
        assertTrue(vm.getErrors().getValue().containsKey("remark"));
        vm.amountText.setValue("0"); vm.refundDate.setValue("2026-10-20"); vm.remark.setValue(""); vm.submit();
        assertTrue(vm.getErrors().getValue().containsKey("amount"));
        assertTrue(vm.getErrors().getValue().containsKey("refund_date"));
        verify(repository, never()).registerReturnRefund(anyLong(), any(), anyString());
    }

    @Test public void permissionsNonReturnAndChangedFarmPreventSubmission() {
        ReturnRefundViewModel vm = model(new SavedStateHandle());
        StockOutboundVO value = record(false); value.actions.canRefund = false; vm.bind(value);
        assertFalse(vm.open(false));
        value.actions.canRefund = true; value.materialType = "FEED";
        assertFalse(ReturnRefundViewModel.canRegister(value));
        vm.bind(record(false)); vm.open(false); vm.amountText.setValue("100");
        when(farm.currentFarmId()).thenReturn(200L); vm.submit();
        assertTrue(vm.getErrors().getValue().get("submit").contains("养殖场已切换"));
        verify(repository, never()).registerReturnRefund(anyLong(), any(), anyString());
    }

    @Test public void authorizedCorrectionPrefillsActualValuesAndUsesPutWithStableRetryKey() {
        ReturnRefundViewModel vm = model(new SavedStateHandle()); vm.bind(record(true));
        assertTrue(vm.open(true)); assertEquals("100.00", vm.amountText.getValue());
        vm.amountText.setValue("105"); vm.remark.setValue("核对银行到账");
        when(repository.updateReturnRefund(eq(74L), any(), anyString())).thenReturn(Single.error(new SocketTimeoutException()));
        vm.submit(); vm.submit();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ReturnRefundRequest> request = ArgumentCaptor.forClass(ReturnRefundRequest.class);
        verify(repository, times(2)).updateReturnRefund(eq(74L), request.capture(), keys.capture());
        assertEquals(new BigDecimal("105.00"), request.getValue().amount);
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        verify(repository, never()).registerReturnRefund(anyLong(), any(), anyString());
        StockOutboundVO expired = record(true); expired.actions.canEditRefund = false; vm.bind(expired);
        assertFalse(vm.open(true)); vm.submit();
        verify(repository, times(2)).updateReturnRefund(eq(74L), any(), anyString());
    }
}
