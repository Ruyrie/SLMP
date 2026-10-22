package com.zhimu.slmp.herd.ui.entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.PenRepository;
import com.zhimu.slmp.herd.model.request.BatchCreateRequest;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.model.vo.PenOptionVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * L08 批次入栏的超容量确认流程：本地横幅与勾选、42206 重写横幅并清除勾选、勾选后带 capacity_confirmed 换新幂等键重新提交。
 */
public class BatchEntryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String CATEGORIES = "[{'id':101,'name':'育肥猪','species_text':'猪','count_unit':'HEAD',"
            + "'count_unit_text':'头','is_breeding':false,'breeds':[{'id':1101,'name':'杜长大'}]},"
            + "{'id':102,'name':'种猪','species_text':'猪','count_unit':'HEAD','count_unit_text':'头',"
            + "'is_breeding':true,'breeds':[{'id':1201,'name':'长大二元'}]}]";
    private static final String PENS = "[{'pen_id':2004,'pen_code':'YF-04','pen_name':'4 号育肥舍','stock_count':150,"
            + "'capacity':220,'disabled':false},{'pen_id':2005,'pen_code':'YF-05','pen_name':'5 号育肥舍',"
            + "'stock_count':0,'capacity':400,'disabled':false}]";
    private static final String RESULT = "{'batch_id':3058,'pen_id':2004,'batch_no':'ZM2610-058','pen_name':'4 号育肥舍',"
            + "'entry_count':60,'pen_stock_after':240,'capacity':220,'over_capacity_count':20}";

    private BatchRepository batchRepo;
    private BatchEntryViewModel vm;

    @Before
    public void setUp() {
        batchRepo = mock(BatchRepository.class);
        PenRepository penRepo = mock(PenRepository.class);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        FarmContext farm = mock(FarmContext.class);
        List<CategoryVO> categories = TestRules.parse(CATEGORIES, new TypeToken<List<CategoryVO>>() {
        }.getType());
        List<PenOptionVO> pens = TestRules.parse(PENS, new TypeToken<List<PenOptionVO>>() {
        }.getType());
        when(baseData.getCategories()).thenReturn(Single.just(categories));
        when(penRepo.getPenOptions(isNull(), isNull())).thenReturn(Single.just(pens));
        when(penRepo.getPenOptions(eq(101L), isNull())).thenReturn(Single.just(pens));
        vm = new BatchEntryViewModel(batchRepo, penRepo, baseData, farm);
        // 派生 LiveData 需要有观察者才会计算
        vm.getCapacityBanner().observeForever(v -> {
        });
        vm.getCanSubmit().observeForever(v -> {
        });
        vm.init(null, null);
    }

    /** 选 4 号育肥舍（150 / 220）、杜长大，填日龄与单价 */
    private void fillForm(String count) {
        vm.onBreedSelected(1101L);
        vm.onPenSelected(2004L);
        vm.getEntryAgeText().setValue("70");
        vm.getUnitPriceText().setValue("520");
        vm.getEntryCountText().setValue(count);
    }

    private static ApiException capacityError() {
        return new ApiException(ErrorCodes.CAPACITY_CONFIRM, "入栏后超过栏舍容量。", null,
                JsonParser.parseString("{\"need_confirm\":true,\"pen_id\":2004,\"capacity\":220,\"current\":180,"
                        + "\"after\":240,\"over\":20}"), null, null);
    }

    @Test
    public void localBanner_requiresConfirmBeforeSubmit() {
        fillForm("90");

        String banner = TestRules.plain(vm.getCapacityBanner().getValue());
        assertNotNull(banner);
        assertTrue(banner.contains("4 号育肥舍容量 220 头，现有 150 头，入栏后共 240 头，超出 20 头"));
        assertFalse(vm.getCanSubmit().getValue());

        vm.getCapacityChecked().setValue(true);
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void bannerCollapses_clearsCheck() {
        fillForm("90");
        vm.getCapacityChecked().setValue(true);

        vm.getEntryCountText().setValue("50");

        assertNull(vm.getCapacityBanner().getValue());
        assertFalse(vm.getCapacityChecked().getValue());
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void serverCapacityConfirm_rewritesBanner_thenResubmitsWithNewKey() {
        BatchEntryResultVO result = TestRules.parse(RESULT, BatchEntryResultVO.class);
        when(batchRepo.createBatch(any(BatchCreateRequest.class), anyString()))
                .thenReturn(Single.error(capacityError()), Single.just(result));
        // 本地 150 + 60 = 210 未超出，第一次提交不带 capacity_confirmed
        fillForm("60");
        assertNull(vm.getCapacityBanner().getValue());

        vm.submit();

        String banner = TestRules.plain(vm.getCapacityBanner().getValue());
        assertNotNull(banner);
        assertTrue(banner.contains("现有 180 头，入栏后共 240 头，超出 20 头"));
        assertFalse(vm.getCapacityChecked().getValue());
        assertFalse(vm.getCanSubmit().getValue());
        assertFalse(vm.getSubmitting().getValue());
        assertNull(vm.getSuccessEvent().getValue());

        vm.getCapacityChecked().setValue(true);
        vm.submit();

        ArgumentCaptor<BatchCreateRequest> req = ArgumentCaptor.forClass(BatchCreateRequest.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(batchRepo, times(2)).createBatch(req.capture(), key.capture());
        assertNull(req.getAllValues().get(0).capacityConfirmed);
        assertEquals(Boolean.TRUE, req.getAllValues().get(1).capacityConfirmed);
        assertEquals(60, req.getAllValues().get(1).entryCount);
        assertNotEquals(key.getAllValues().get(0), key.getAllValues().get(1));
        assertEquals("ZM2610-058", vm.getSuccessEvent().getValue().getBatchNo());
    }

    @Test
    public void serverBanner_dropsWhenCountChanges() {
        when(batchRepo.createBatch(any(BatchCreateRequest.class), anyString()))
                .thenReturn(Single.error(capacityError()));
        fillForm("60");
        vm.submit();
        assertNotNull(vm.getCapacityBanner().getValue());

        // 改数量后按本地计算：150 + 40 = 190，不超出
        vm.getEntryCountText().setValue("40");

        assertNull(vm.getCapacityBanner().getValue());
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void unconfirmedOverCapacity_doesNotSubmit() {
        fillForm("90");

        vm.submit();

        verify(batchRepo, never()).createBatch(any(BatchCreateRequest.class), anyString());
    }

    @Test
    public void validate_requiredFields() {
        vm.getEntryCountText().setValue("0");

        vm.submit();

        assertEquals("请选择栏舍", vm.getFieldErrors().getValue().get(BatchEntryViewModel.F_PEN));
        assertEquals("请选择品种", vm.getFieldErrors().getValue().get(BatchEntryViewModel.F_BREED));
        assertEquals("请填写入栏日龄", vm.getFieldErrors().getValue().get(BatchEntryViewModel.F_AGE));
        assertEquals("数量需为大于 0 的整数", vm.getFieldErrors().getValue().get(BatchEntryViewModel.F_COUNT));
        assertEquals("请填写引种单价", vm.getFieldErrors().getValue().get(BatchEntryViewModel.F_PRICE));
    }
}
