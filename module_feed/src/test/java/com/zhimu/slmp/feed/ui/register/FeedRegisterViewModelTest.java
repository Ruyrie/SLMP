package com.zhimu.slmp.feed.ui.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.FeedExtras;
import com.zhimu.slmp.feed.TestRules;
import com.zhimu.slmp.feed.data.FeedRepository;
import com.zhimu.slmp.feed.model.request.FeedRecordCreateRequest;
import com.zhimu.slmp.feed.model.vo.FeedRecordDetailVO;
import com.zhimu.slmp.feed.model.vo.FeedRecordLookupVO;
import com.zhimu.slmp.feed.model.vo.FeedTaskFormVO;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * G02 投喂表单校验与提交：必填与大于 0、超结存禁用并提示、偏差与投喂异常横幅、养殖户带执行人与代登记提示、
 * 员工不传执行人；幂等键同内容沿用、改动后换新；读超时先查 API-G08 再同键重发；修改模式同一饲料按差值校验结存。
 */
public class FeedRegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private FeedRepository repo;
    private FarmContext farm;
    private FeedRegisterViewModel vm;

    @Before
    public void setUp() throws IOException {
        repo = mock(FeedRepository.class);
        farm = mock(FarmContext.class);
        AppDatabase db = mock(AppDatabase.class);
        FormDraftDao dao = mock(FormDraftDao.class);
        when(db.formDraftDao()).thenReturn(dao);
        when(dao.get(anyString(), anyLong(), anyString())).thenReturn(Maybe.empty());
        when(dao.delete(anyString(), anyLong(), anyString())).thenReturn(Completable.complete());
        when(dao.deleteOlderThan(anyLong())).thenReturn(Completable.complete());
        when(dao.save(any())).thenReturn(Completable.complete());
        when(farm.farmIdOrZero()).thenReturn(1001L);
        when(repo.getTaskForm(70108L)).thenReturn(Single.just(TestRules.mock("task_form_70108.json",
                FeedTaskFormVO.class)));
        when(repo.getTaskForm(80108L)).thenReturn(Single.just(TestRules.mock("task_form_70108_staff.json",
                FeedTaskFormVO.class)));
        vm = new FeedRegisterViewModel(repo, farm, db);
        // 派生值由 MediatorLiveData 计算，测试中保持观察
        vm.getCanSubmit().observeForever(v -> {
        });
    }

    private void open(long taskId) {
        Map<String, Object> args = new HashMap<>();
        args.put(RouterPath.EXTRA_TASK_ID, taskId);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    private ApiResponse<FeedRecordDetailVO> created(String message) throws IOException {
        return new ApiResponse<>(0, message, TestRules.mock("record_created_proxy_li.json", FeedRecordDetailVO.class),
                "t");
    }

    @Test
    public void createMode_prefillsTaskAndPlanSource() {
        open(70108L);
        assertEquals(FeedRegisterMode.CREATE, vm.getMode().getValue());
        assertEquals("3 号育肥舍 · 午餐 11:30", vm.getTaskCard().getValue().title);
        assertEquals("ZM2610-031 · 杜长大育肥猪 · 96 日龄 · 存栏 200 头",
                TestRules.plain(vm.getTaskCard().getValue().subtitle));
        assertEquals("160 千克", TestRules.plain(vm.getPlanText().getValue()));
        assertEquals("200 头 × 每头日投喂 2.4 千克 ÷ 3 餐", TestRules.plain(vm.getPlanSourceText().getValue()));
        assertEquals("结存 500 千克 · 剩余可用 1 天", TestRules.plain(vm.getStockHint().getValue()));
        assertEquals("李明", vm.getSelectedExecutor().getValue().getName());
        assertEquals("登记人：王建国（代登记）", vm.getProxyHint().getValue());
        assertTrue(vm.getExecutorVisible().getValue());
    }

    @Test
    public void emptyOrZeroActual_showsFieldErrorAndDoesNotSubmit() {
        open(70108L);
        vm.submit();
        assertEquals("请填写实际投喂量", vm.getFieldErrors().getValue().get(FeedRegisterViewModel.F_ACTUAL));
        vm.getActualQty().setValue("0");
        vm.submit();
        assertEquals("实际投喂量要大于 0 千克", vm.getFieldErrors().getValue().get(FeedRegisterViewModel.F_ACTUAL));
        verify(repo, never()).createRecord(any(), anyString());
    }

    @Test
    public void actualOverStock_disablesSubmitAndShowsErrorBanner() {
        open(70108L);
        vm.getActualQty().setValue("560");
        assertEquals("不能超过结存 500 千克", TestRules.plain(vm.getStockError().getValue()));
        assertEquals("本次需要 560 千克，结存 500 千克，请先登记入库。", TestRules.plain(vm.getOutboundError().getValue()));
        assertFalse(vm.getCanSubmit().getValue());
        vm.getActualQty().setValue("140");
        assertNull(vm.getStockError().getValue());
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void deviationOverThreshold_showsAbnormalBanner_stillSubmits() throws IOException {
        open(70108L);
        vm.getActualQty().setValue("140");
        assertEquals("实际比计划少 12.5%，超过 10%。确认后这次投喂会标为投喂异常。", vm.getAbnormalText().getValue());
        assertEquals("200 头 × 每头日投喂 2.4 千克 ÷ 3 餐 · 偏差 -12.5%",
                TestRules.plain(vm.getPlanSourceText().getValue()));
        assertEquals("确认后自动生成 140 千克饲喂领用出库，待办自动完成。员工确认后不能再修改；你可以修改或作废当天的记录，出库同步更正。",
                TestRules.plain(vm.getOutboundText().getValue()));
        when(repo.createRecord(any(), anyString())).thenReturn(Single.just(created("已代李明登记投喂，待办已自动完成")));

        vm.submit();

        ArgumentCaptor<FeedRecordCreateRequest> req = ArgumentCaptor.forClass(FeedRecordCreateRequest.class);
        verify(repo).createRecord(req.capture(), anyString());
        assertEquals(70108L, req.getValue().taskId);
        assertEquals(4001L, req.getValue().feedId);
        assertEquals(0, new BigDecimal("140").compareTo(req.getValue().actualQty));
        assertEquals(Long.valueOf(10003L), req.getValue().executorId);
        UiMessage msg = vm.getCloseWithResult().getValue();
        assertNotNull(msg);
        assertEquals("已代李明登记投喂，待办已自动完成", msg.getText());
        assertNull(vm.pendingIdemKey());
    }

    @Test
    public void staff_noExecutorInRequest_andStaffTexts() throws IOException {
        open(80108L);
        assertFalse(vm.getExecutorVisible().getValue());
        assertEquals("登记人：李明", vm.getProxyHint().getValue());
        vm.getActualQty().setValue("560");
        assertEquals("本次需要 560 千克，结存 500 千克，请联系所有者或场长先登记入库。",
                TestRules.plain(vm.getOutboundError().getValue()));
        vm.getActualQty().setValue("160");
        when(repo.createRecord(any(), anyString())).thenReturn(Single.just(created("已登记投喂，待办已自动完成")));
        vm.submit();
        ArgumentCaptor<FeedRecordCreateRequest> req = ArgumentCaptor.forClass(FeedRecordCreateRequest.class);
        verify(repo).createRecord(req.capture(), anyString());
        assertNull(req.getValue().executorId);
    }

    @Test
    public void idemKey_reusedForSameContent_renewedAfterChange() {
        open(70108L);
        vm.getActualQty().setValue("158");
        when(repo.createRecord(any(), anyString())).thenReturn(Single.error(ApiException.network(null)));
        vm.submit();
        String first = vm.pendingIdemKey();
        assertNotNull(first);
        vm.submit();
        assertEquals(first, vm.pendingIdemKey());
        vm.getActualQty().setValue("159");
        vm.submit();
        assertNotEquals(first, vm.pendingIdemKey());
    }

    @Test
    public void timeout_looksUpThenResendsWithSameKey() throws IOException {
        open(70108L);
        vm.getActualQty().setValue("158");
        ApiException timeout = ApiException.network(new SocketTimeoutException("timeout"));
        FeedRecordLookupVO notSaved = TestRules.mock("lookup_not_saved.json", FeedRecordLookupVO.class);
        when(repo.createRecord(any(), anyString())).thenReturn(Single.error(timeout))
                .thenReturn(Single.just(created("已登记投喂，待办已自动完成")));
        when(repo.lookupRecord(anyString())).thenReturn(Single.just(notSaved));

        vm.submit();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repo).lookupRecord(anyString());
        verify(repo, times(2)).createRecord(any(), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals("已登记投喂，待办已自动完成", vm.getCloseWithResult().getValue().getText());
    }

    @Test
    public void stockPrecheckError_marksActualAndClearsKey() {
        open(70108L);
        vm.getActualQty().setValue("158");
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("feed_id", 4001);
        data.addProperty("stock_qty", "120");
        data.addProperty("need_qty", "158");
        ApiException e = new ApiException(ErrorCodes.STOCK_NOT_ENOUGH,
                "库存不足，不能确认投喂。本次需要 158 千克，结存 120 千克，请先登记入库。", "t", data, null, null);
        when(repo.createRecord(any(), anyString())).thenReturn(Single.error(e));
        vm.submit();
        assertEquals("不能超过结存 120 千克", TestRules.plain(vm.getFieldErrors().getValue()
                .get(FeedRegisterViewModel.F_ACTUAL)));
        assertNull(vm.pendingIdemKey());
        assertFalse(vm.getSubmitting().getValue());
    }

    @Test
    public void editSameFeed_checksDifferenceAgainstStock() throws IOException {
        when(repo.getRecord(7106L)).thenReturn(Single.just(TestRules.mock("record_7106.json", FeedRecordDetailVO.class)));
        when(repo.getTaskForm(70107L)).thenReturn(Single.just(TestRules.mock("task_form_70107.json",
                FeedTaskFormVO.class)));
        Map<String, Object> args = new HashMap<>();
        args.put(FeedExtras.EXTRA_RECORD_ID, 7106L);
        vm.attachSavedState(new SavedStateHandle(args));

        assertEquals(FeedRegisterMode.EDIT, vm.getMode().getValue());
        assertEquals("158", vm.getActualQty().getValue());
        // 640 − 原 158 = 482 ≤ 结存 500
        vm.getActualQty().setValue("640");
        assertNull(vm.getStockError().getValue());
        assertEquals("保存后饲喂领用出库同步更正为 640 千克。", TestRules.plain(vm.getOutboundText().getValue()));
        vm.getActualQty().setValue("660");
        assertEquals("不能超过结存 500 千克", TestRules.plain(vm.getStockError().getValue()));
        assertTrue(vm.getCanVoid().getValue());
    }
}
