package com.zhimu.slmp.stock.ui.feedinbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.stock.StockExtras;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.request.FeedInboundRequest;
import com.zhimu.slmp.stock.model.vo.FeedOptionVO;
import com.zhimu.slmp.stock.model.vo.RequestStatusVO;
import com.zhimu.slmp.stock.model.vo.StockInboundVO;
import com.zhimu.slmp.stock.ui.common.FormMode;
import com.zhimu.slmp.stock.ui.common.StockFormViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * M03 饲料入库：预选与辅助文字、必填与日期校验、入库金额与同步记账说明、成功文案（带“查看”E04）；
 * 提交防重：网络失败保留表单与幂等键（重试沿用）、读超时查 API-M42（DONE 取回单据按成功处理、NOT_FOUND 提示重试并保留键）；
 * 修改 40307 弹“需要提交修改申请”。
 */
public class FeedInboundViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private StockRepository repo;
    private FeedInboundViewModel vm;
    private StockInboundVO created;

    @Before
    public void setUp() throws IOException {
        repo = mock(StockRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        List<FeedOptionVO> options = TestRules.mock("feed_options_inbound.json",
                new TypeToken<List<FeedOptionVO>>() {
                }.getType());
        when(repo.listFeedOptions(eq("INBOUND"), isNull())).thenReturn(Single.just(options));
        when(repo.recentPartners(anyString(), any())).thenReturn(Single.just(java.util.Collections.emptyList()));
        created = TestRules.mock("inbound_created_feed.json", StockInboundVO.class);
        vm = new FeedInboundViewModel(repo, TestRules.noDrafts(), farm);
        vm.getAmountText().observeForever(v -> {
        });
    }

    private void open(Long feedId, Long recordId) {
        Map<String, Object> args = new HashMap<>();
        if (feedId != null) {
            args.put(StockExtras.EXTRA_FEED_ID, feedId);
        }
        if (recordId != null) {
            args.put(StockExtras.EXTRA_RECORD_ID, recordId);
        }
        vm.attachSavedState(new SavedStateHandle(args));
    }

    private void fill() {
        vm.getInputQty().setValue("1000");
        vm.getUnitPrice().setValue("3.20");
        vm.getSupplierName().setValue("松山区丰禾饲料经销部");
    }

    @Test
    public void preselectFeed_showsStockHint() {
        open(5101L, null);
        assertEquals(FormMode.NEW, vm.getMode().getValue());
        assertEquals(5101, vm.getSelectedFeed().getValue().feedId);
        assertEquals("当前结存 500 千克，剩余可用 1 天", TestRules.plain(vm.getFeedHint().getValue()));
        assertEquals("2026-10-19", vm.getInboundDate().getValue());
    }

    @Test
    public void emptyForm_showsFieldErrors() {
        open(null, null);
        vm.submit();
        Map<String, String> e = vm.getFieldErrors().getValue();
        assertEquals("请选择饲料", e.get(FeedInboundViewModel.F_FEED));
        assertEquals("必须大于 0", e.get(FeedInboundViewModel.F_QTY));
        assertEquals("必须大于 0", e.get(FeedInboundViewModel.F_PRICE));
        assertEquals("请填写供应商", e.get(FeedInboundViewModel.F_SUPPLIER));
        assertEquals(FeedInboundViewModel.F_FEED, vm.getFocusField().getValue());
        verify(repo, never()).createFeedInbound(any(), anyString());
    }

    @Test
    public void dates_validatedImmediately() {
        open(5101L, null);
        vm.onDatePicked(FeedInboundViewModel.F_PRODUCTION, "2026-10-12");
        vm.onDatePicked(FeedInboundViewModel.F_SHELF, "2026-10-12");
        assertEquals("必须晚于生产日期", vm.getFieldErrors().getValue().get(FeedInboundViewModel.F_SHELF));
        vm.onDatePicked(FeedInboundViewModel.F_PRODUCTION, null);
        vm.onDatePicked(FeedInboundViewModel.F_SHELF, "2026-10-18");
        assertEquals("已过保质期，不能入库", vm.getFieldErrors().getValue().get(FeedInboundViewModel.F_SHELF));
        vm.onDatePicked(FeedInboundViewModel.F_PRODUCTION, "2026-10-20");
        assertEquals("生产日期不能晚于入库日期", vm.getFieldErrors().getValue().get(FeedInboundViewModel.F_PRODUCTION));
    }

    @Test
    public void amount_andSyncTextFollowInput() {
        open(5101L, null);
        assertEquals("—", vm.getAmountText().getValue());
        fill();
        assertEquals("3,200.00", vm.getAmountText().getValue());
        assertTrue(TestRules.plain(vm.getSyncText().getValue()).contains("饲料采购 3,200 元"));
    }

    @Test
    public void submit_success_messageWithE04AndKeyCleared() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString())).thenReturn(Single.just(created));
        vm.submit();
        UiMessage msg = vm.getSubmitted().getValue();
        assertEquals("已入库 1,000 千克育肥全价料，已同步记一笔支出", TestRules.plain(msg.getText()));
        assertEquals("查看", msg.getActionText());
        assertEquals(RouterPath.E04, msg.getActionId());
        assertNull(vm.pendingIdemKey());
        assertFalse(vm.getSubmitting().getValue());
    }

    @Test
    public void networkFailure_keepsFormAndReusesKey() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new IOException("down"))))
                .thenReturn(Single.just(created));
        vm.submit();
        assertEquals(StockFormViewModel.MSG_NETWORK_KEEP, vm.getMessage().getValue().getText());
        String key = vm.pendingIdemKey();
        assertNotNull(key);
        assertEquals("1000", vm.getInputQty().getValue());
        vm.retrySubmit();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repo, times(2)).createFeedInbound(any(FeedInboundRequest.class), keys.capture());
        assertEquals(key, keys.getAllValues().get(0));
        assertEquals(key, keys.getAllValues().get(1));
        assertNotNull(vm.getSubmitted().getValue());
    }

    @Test
    public void timeout_queriesRequestStatus_doneTreatedAsSuccess() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new SocketTimeoutException("timeout"))));
        RequestStatusVO done = new RequestStatusVO();
        done.status = "DONE";
        done.bizType = "INBOUND";
        done.bizId = 6230L;
        when(repo.getRequestStatus(anyString())).thenReturn(Single.just(done));
        when(repo.getInbound(6230L)).thenReturn(Single.just(created));
        vm.submit();
        verify(repo).getInbound(6230L);
        assertNotNull(vm.getSubmitted().getValue());
    }

    @Test
    public void timeout_notFound_promptsRetryAndKeepsKey() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new SocketTimeoutException("timeout"))));
        RequestStatusVO nf = new RequestStatusVO();
        nf.status = "NOT_FOUND";
        when(repo.getRequestStatus(anyString())).thenReturn(Single.just(nf));
        vm.submit();
        assertEquals(StockFormViewModel.MSG_NOT_SAVED, vm.getMessage().getValue().getText());
        assertNotNull(vm.pendingIdemKey());
        assertFalse(vm.getSubmitting().getValue());
        verify(repo, never()).getInbound(anyLong());
    }

    @Test
    public void processing_pollsUpToFiveTimes() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString()))
                .thenReturn(Single.error(ApiException.network(new SocketTimeoutException("timeout"))));
        RequestStatusVO p = new RequestStatusVO();
        p.status = "PROCESSING";
        when(repo.getRequestStatus(anyString())).thenReturn(Single.just(p));
        vm.submit();
        verify(repo, times(5)).getRequestStatus(anyString());
        assertEquals(StockFormViewModel.MSG_NOT_SAVED, vm.getMessage().getValue().getText());
    }

    @Test
    public void changedContent_getsNewKeyAfterBusinessError() {
        open(5101L, null);
        fill();
        when(repo.createFeedInbound(any(), anyString()))
                .thenReturn(Single.error(new ApiException(ErrorCodes.NOT_FOUND, "饲料不存在")));
        vm.submit();
        assertNull(vm.pendingIdemKey());
    }

    @Test
    public void view_editAndChangeRequestRequired() throws IOException {
        StockInboundVO r = TestRules.mock("inbound_6201.json", StockInboundVO.class);
        when(repo.getInbound(6201L)).thenReturn(Single.just(r));
        open(null, 6201L);
        assertEquals(FormMode.VIEW, vm.getMode().getValue());
        assertEquals("REQUEST_ONLY", vm.actions().editState);
        vm.startEdit();
        assertEquals(FormMode.EDIT, vm.getMode().getValue());
        assertEquals("2000", vm.getInputQty().getValue());
        when(repo.updateInbound(anyLong(), any())).thenReturn(Single.error(
                new ApiException(ErrorCodes.CHANGE_REQUEST_REQUIRED, "已发生出库，需要提交修改申请")));
        vm.submit();
        assertEquals("已发生出库，需要提交修改申请", vm.getNeedRequest().getValue());
        assertTrue(vm.cancelEdit());
        vm.onVoidMenu();
        assertEquals("作废后结存减少 2,000 千克，同步记账生成的饲料采购 6,400 元同时作废。",
                TestRules.plain(vm.getConfirmVoid().getValue()));
    }
}
