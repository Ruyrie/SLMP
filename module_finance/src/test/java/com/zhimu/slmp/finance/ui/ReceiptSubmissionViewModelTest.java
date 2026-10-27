package com.zhimu.slmp.finance.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.data.SaleOrderRepository;
import com.zhimu.slmp.finance.model.vo.SaleOrderDetailVO;
import com.zhimu.slmp.finance.model.vo.SalePrefillVO;
import com.zhimu.slmp.finance.ui.common.ReceiptHost;
import com.zhimu.slmp.finance.ui.receivable.ReceivableListViewModel;
import com.zhimu.slmp.finance.ui.saledetail.SaleOrderDetailViewModel;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.ReceiptFormDelegate;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

/** Both receipt entry points must close after saving and expose failures in the still-open form. */
@RunWith(Parameterized.class)
public class ReceiptSubmissionViewModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static List<String> hosts() {
        return Arrays.asList("detail", "receivables");
    }

    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();

    private final String entryPoint;
    private SaleOrderRepository repository;
    private ReceiptHost host;
    private ReceiptFormDelegate form;
    private SaleOrderDetailVO paid;
    private final List<Boolean> sheetEvents = new ArrayList<>();

    public ReceiptSubmissionViewModelTest(String entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Before public void openForm() throws IOException {
        repository = mock(SaleOrderRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        when(baseData.getDicts(anyString(), any())).thenReturn(Single.just(Collections.emptyList()));
        SaleOrderDetailVO order = TestRules.mock("order_9102.json", SaleOrderDetailVO.class);
        paid = TestRules.mock("receipt_9102_paid.json", SaleOrderDetailVO.class);
        when(repository.getOrderDetail(9102L)).thenReturn(Single.just(order));
        when(repository.getPrefill(any(), any())).thenReturn(
                Single.just(TestRules.mock("prefill.json", SalePrefillVO.class)));
        Map<String, Object> args = new HashMap<>();
        args.put(RouterPath.EXTRA_SALE_ORDER_ID, 9102L);
        if ("detail".equals(entryPoint)) {
            SaleOrderDetailViewModel vm = new SaleOrderDetailViewModel(repository, baseData, farm);
            vm.attachSavedState(new SavedStateHandle(args));
            host = vm;
        } else {
            ReceivableListViewModel vm = new ReceivableListViewModel(repository, baseData, farm);
            vm.attachSavedState(new SavedStateHandle(args));
            host = vm;
        }
        form = host.getReceipt();
        // Match the Activity's lifecycle-aware consumption of SingleLiveEvent (observeForever bypasses it).
        ActiveOwner owner = new ActiveOwner();
        form.getShowSheet().observe(owner, sheetEvents::add);
        if (host instanceof SaleOrderDetailViewModel) {
            ((SaleOrderDetailViewModel) host).openReceipt();
        } else {
            ((ReceivableListViewModel) host).openReceipt(order);
        }
        assertEquals(Collections.singletonList(true), sheetEvents);
    }

    @Test public void successfulReceipt_closesSheetAndStopsSubmitting() {
        when(repository.addReceipt(eq(9102L), any(), anyString())).thenReturn(Single.just(paid));
        host.confirmReceipt();
        assertEquals(Arrays.asList(true, false), sheetEvents);
        assertFalse(form.getSubmitting().getValue());
        assertTrue(form.getErrors().getValue().isEmpty());
        verify(repository).addReceipt(eq(9102L), any(), anyString());
        if (host instanceof SaleOrderDetailViewModel) {
            assertEquals(paid, ((SaleOrderDetailViewModel) host).getDetail().getValue());
        }
    }

    @Test public void secondClickWhileSaving_doesNotCreateAnotherReceipt() {
        SingleSubject<SaleOrderDetailVO> request = SingleSubject.create();
        when(repository.addReceipt(eq(9102L), any(), anyString())).thenReturn(request);
        host.confirmReceipt();
        host.confirmReceipt();
        assertTrue(form.getSubmitting().getValue());
        assertEquals(Collections.singletonList(true), sheetEvents);
        verify(repository).addReceipt(eq(9102L), any(), anyString());
        request.onSuccess(paid);
        assertEquals(Arrays.asList(true, false), sheetEvents);
    }

    @Test public void networkFailure_isVisibleInFormAndRetryReusesIdempotencyKey() {
        when(repository.addReceipt(eq(9102L), any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new IOException("offline"))), Single.just(paid));
        String amount = form.amountText.getValue();
        host.confirmReceipt();
        assertEquals(FinanceErrors.MSG_NETWORK_KEPT, form.getErrors().getValue().get(ReceiptFormDelegate.F_SUBMIT));
        assertEquals(amount, form.amountText.getValue());
        assertEquals(Collections.singletonList(true), sheetEvents);
        assertFalse(form.getSubmitting().getValue());
        host.confirmReceipt();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).addReceipt(eq(9102L), any(), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertTrue(form.getErrors().getValue().isEmpty());
        assertEquals(Arrays.asList(true, false), sheetEvents);
    }

    @Test public void timeoutStatusCheckFailure_keepsFormWithVisibleWarning() {
        when(repository.addReceipt(eq(9102L), any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new SocketTimeoutException())));
        when(repository.getSubmission(anyString())).thenReturn(Single.error(new IOException("offline")));
        host.confirmReceipt();
        assertEquals(FinanceErrors.MSG_TIMEOUT, form.getErrors().getValue().get(ReceiptFormDelegate.F_SUBMIT));
        assertFalse(form.getSubmitting().getValue());
        assertEquals(Collections.singletonList(true), sheetEvents);
    }

    @Test public void businessFailure_isVisibleInForm() {
        when(repository.addReceipt(eq(9102L), any(), anyString()))
                .thenReturn(Single.error(new ApiException(50001, "暂时无法登记收款")));
        host.confirmReceipt();
        assertEquals("暂时无法登记收款", form.getErrors().getValue().get(ReceiptFormDelegate.F_SUBMIT));
        assertEquals(Collections.singletonList(true), sheetEvents);
        assertFalse(form.getSubmitting().getValue());
    }

    @Test public void changedOrder_closesSheetInsteadOfAllowingStaleAmountRetry() {
        when(repository.addReceipt(eq(9102L), any(), anyString()))
                .thenReturn(Single.error(new ApiException(ErrorCodes.STATE_CHANGED, "出栏单已收齐")));
        host.confirmReceipt();
        assertEquals(Arrays.asList(true, false), sheetEvents);
        assertFalse(form.getSubmitting().getValue());
    }

    @Test public void invalidAmount_remainsInlineAndNeverCallsReceiptApi() {
        form.amountText.setValue("88160.01");
        host.confirmReceipt();
        assertTrue(form.getErrors().getValue().containsKey(ReceiptFormDelegate.F_AMOUNT));
        assertEquals(Collections.singletonList(true), sheetEvents);
        verify(repository, never()).addReceipt(eq(9102L), any(), anyString());
    }

    private static final class ActiveOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);

        ActiveOwner() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
        }

        @Override public Lifecycle getLifecycle() {
            return lifecycle;
        }
    }
}
