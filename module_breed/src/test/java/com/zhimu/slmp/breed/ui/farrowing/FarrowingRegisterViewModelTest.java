package com.zhimu.slmp.breed.ui.farrowing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.TestRules;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.FarrowingRequest;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.breed.ui.common.FinishEvent;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import io.reactivex.rxjava3.core.Single;

/**
 * B04 登记产仔的提交：和校验、转批次成功（带批次）、不转批次的提示、42206 超容量确认后换新幂等键重提。
 */
public class FarrowingRegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private BreedRepository repo;
    private FarrowingRegisterViewModel vm;

    @Before
    public void setUp() throws Exception {
        repo = mock(BreedRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        when(repo.getRegisterContext(4131L, BreedCodes.TYPE_FARROWING)).thenReturn(Single.just(
                TestRules.data("ctx_farrowing_0131.json", RegisterContextVO.class)));
        vm = new FarrowingRegisterViewModel(repo, farm, mock(BaseDataRepository.class));
        vm.getSumError().observeForever(v -> { });
        vm.getCapacity().observeForever(v -> { });
        vm.getCapacityText().observeForever(v -> { });
        vm.getEntryText().observeForever(v -> { });
        vm.getStatusHint().observeForever(v -> { });
        vm.attachSavedState(new SavedStateHandle(Collections.singletonMap(BreedExtras.EXTRA_ANIMAL_ID, 4131L)));
    }

    private void fill(String total, String live, String still) {
        vm.getTotalBorn().setValue(total);
        vm.getLiveBorn().setValue(live);
        vm.getStillborn().setValue(still);
    }

    @Test
    public void contextAndDerivedTexts() {
        assertEquals("登记后 0131 转为哺乳，预产期待办自动完成。", vm.getStatusHint().getValue());
        assertEquals("产房", vm.getTargetPen().getValue().getPenName());
        fill("13", "11", "1");
        assertEquals("活仔 11 头 + 死胎 1 头 = 12 头，与总产仔 13 头不一致", vm.getSumError().getValue());
        assertEquals("11 头（按活仔数）", TestRules.plain(vm.getEntryText().getValue()));
        fill("13", "12", "1");
        assertNull(vm.getSumError().getValue());
    }

    @Test
    public void submit_sumMismatch_doesNotCallServer() {
        fill("13", "11", "1");
        vm.submit();
        assertNotNull(vm.getFieldErrors().getValue().get("live_born"));
        verify(repo, never()).createFarrowing(any(), anyString());
    }

    @Test
    public void submit_transfer_finishesWithBatch() throws Exception {
        when(repo.createFarrowing(any(FarrowingRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("farrowing_ok.json", RegisterResultVO.class)));
        fill("13", "12", "1");
        vm.submit();
        ArgumentCaptor<FarrowingRequest> req = ArgumentCaptor.forClass(FarrowingRequest.class);
        verify(repo).createFarrowing(req.capture(), anyString());
        assertEquals(Long.valueOf(2007), req.getValue().getTargetPenId());
        assertTrue(req.getValue().getTransferToBatch());
        FinishEvent done = vm.getFinishWithMessage().getValue();
        assertEquals("已登记产仔，新批次已入栏", done.getText());
        assertEquals(Long.valueOf(3071), done.getBatchId());
    }

    @Test
    public void submit_withoutTransfer_messageNamesSow() throws Exception {
        when(repo.createFarrowing(any(FarrowingRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("farrowing_no_batch.json", RegisterResultVO.class)));
        fill("13", "12", "1");
        vm.onTransferChanged(false);
        vm.submit();
        assertEquals("已登记产仔，0131 转为哺乳", vm.getFinishWithMessage().getValue().getText());
        assertNull(vm.getFinishWithMessage().getValue().getBatchId());
    }

    @Test
    public void capacity_localWarning_blocksUntilConfirmed() {
        // 产房容量 40 头、现有 0 头：活仔 45 头本地预判超容量，未勾选不能提交
        fill("50", "45", "5");
        assertEquals("入栏后超过栏舍容量。产房容量 40 头，现有 0 头，入栏后共 45 头，超出 5 头。",
                TestRules.plain(vm.getCapacityText().getValue()));
        vm.submit();
        assertEquals("请先确认超出容量", vm.getFieldErrors().getValue().get("capacity_confirmed"));
        verify(repo, never()).createFarrowing(any(), anyString());
    }

    @Test
    public void capacity_server42206_confirmResubmitsWithNewKey() throws Exception {
        ApiException e = new ApiException(ErrorCodes.CAPACITY_CONFIRM, "入栏后超过栏舍容量。", null,
                TestRules.mock("err_42206_farrowing.json").get("data"), null, null);
        when(repo.createFarrowing(any(FarrowingRequest.class), anyString()))
                .thenReturn(Single.error(e))
                .thenReturn(Single.just(TestRules.data("farrowing_ok.json", RegisterResultVO.class)));
        // 本地按产房 0 / 40 头判断不超容量，服务端按实时存栏返回 42206
        fill("13", "12", "1");
        vm.submit();
        assertNotNull(vm.getCapacity().getValue());
        assertEquals(5, vm.getCapacity().getValue().getOverCount());
        assertFalse(vm.getCapacityConfirmed().getValue());
        assertEquals("请先确认超出容量", vm.getFieldErrors().getValue().get("capacity_confirmed"));
        String firstKey = vm.currentIdemKey();

        vm.onCapacityChecked(true);
        vm.submit();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FarrowingRequest> reqs = ArgumentCaptor.forClass(FarrowingRequest.class);
        verify(repo, times(2)).createFarrowing(reqs.capture(), keys.capture());
        assertNull(reqs.getAllValues().get(0).getCapacityConfirmed());
        assertTrue(reqs.getAllValues().get(1).getCapacityConfirmed());
        assertEquals(firstKey, keys.getAllValues().get(0));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals("已登记产仔，新批次已入栏", vm.getFinishWithMessage().getValue().getText());
    }
}
