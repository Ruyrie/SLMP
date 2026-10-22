package com.zhimu.slmp.breed.ui.incubation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.breed.BreedExtras;
import com.zhimu.slmp.breed.TestRules;
import com.zhimu.slmp.breed.data.BreedRepository;
import com.zhimu.slmp.breed.model.request.CandlingRequest;
import com.zhimu.slmp.breed.model.request.HatchRequest;
import com.zhimu.slmp.breed.model.request.IncubationRequest;
import com.zhimu.slmp.breed.model.vo.FlockOptionVO;
import com.zhimu.slmp.breed.model.vo.IncubationDetailVO;
import com.zhimu.slmp.breed.model.vo.PenOptionVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * B07 孵化三步：入孵推算预计出雏日 → 登记入孵后进入照蛋（受精率实时）→ 登记照蛋后进入出雏（弱雏、孵化率、入栏容量）→
 * 登记出雏后显示结果（Activity 范围 ViewModel，数据取本组件 Mock）。
 */
public class IncubationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private static final String CATEGORIES = "[{\"id\":201,\"name\":\"肉鸡\",\"species\":\"POULTRY\",\"count_unit\":\"BIRD\","
            + "\"is_breeding\":false,\"breeds\":[{\"id\":2101,\"name\":\"白羽肉鸡\",\"incubation_days\":21}]}]";

    private BreedRepository repo;
    private IncubationViewModel vm;

    @Before
    public void setUp() throws Exception {
        repo = mock(BreedRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1002L);
        BaseDataRepository baseData = mock(BaseDataRepository.class);
        List<CategoryVO> categories = GsonFactory.get().fromJson(CATEGORIES,
                new TypeToken<List<CategoryVO>>() { }.getType());
        when(baseData.getCategories()).thenReturn(Single.just(categories));
        PageVO<FlockOptionVO> flocks = TestRules.data("flocks_breeding.json",
                new TypeToken<PageVO<FlockOptionVO>>() { }.getType());
        when(repo.getFlocks()).thenReturn(Single.just(flocks.getList()));
        List<PenOptionVO> pens = TestRules.data("pens_chicken.json", new TypeToken<List<PenOptionVO>>() { }.getType());
        when(repo.getPenOptions(201L)).thenReturn(Single.just(pens));
        for (long id : new long[]{6001, 6003, 6004}) {
            when(repo.getIncubation(id)).thenReturn(Single.just(TestRules.data("incubation_" + id + ".json",
                    IncubationDetailVO.class)));
        }
        vm = new IncubationViewModel(repo, farm, baseData);
        vm.getStepItems().observeForever(v -> { });
        vm.getExpectedHatchPreview().observeForever(v -> { });
        vm.getHatchCardNote().observeForever(v -> { });
        vm.getFertilityText().observeForever(v -> { });
        vm.getWeakCount().observeForever(v -> { });
        vm.getHatchRateText().observeForever(v -> { });
        vm.getHatchedHelper().observeForever(v -> { });
        vm.getTransferDesc().observeForever(v -> { });
        vm.getCapacity().observeForever(v -> { });
        vm.getCapacityText().observeForever(v -> { });
    }

    @Test
    public void step1_projectionThenSet_goesToCandle() throws Exception {
        when(repo.createIncubation(any(IncubationRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("incubation_set_ok.json", RegisterResultVO.class)));
        vm.attachSavedState(new SavedStateHandle());
        assertEquals(1, (int) vm.getCurrentStep().getValue());
        assertEquals(StepItem.CURRENT, vm.getStepItems().getValue().get(0).getState());
        assertNull(vm.getExpectedHatchPreview().getValue());

        vm.submitSet();
        assertEquals("请选择种禽群", vm.getFieldErrors().getValue().get("breeding_batch_id"));
        verify(repo, never()).createIncubation(any(), anyString());

        // 10月19日 + 白羽肉鸡孵化期 21 天 = 11月9日
        vm.onFlockPicked(3212L);
        assertEquals("2026-11-09", vm.getExpectedHatchPreview().getValue());
        assertEquals("按白羽肉鸡孵化期 21 天推算", vm.getHatchCardNote().getValue());
        vm.getSetCount().setValue("9200");
        vm.submitSet();
        verify(repo).getIncubation(6003L);
        assertEquals(2, (int) vm.getCurrentStep().getValue());
        assertEquals(StepItem.DONE, vm.getStepItems().getValue().get(0).getState());
        assertEquals(StepItem.CURRENT, vm.getStepItems().getValue().get(1).getState());
        assertEquals("2 号种鸡舍 · ZM2603-016", vm.getDetail().getValue().getFlockText());
    }

    @Test
    public void step2_candle_liveFertilityThenStep3() throws Exception {
        when(repo.candle(eq(6003L), any(CandlingRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("incubation_6003_candled.json", IncubationDetailVO.class)));
        vm.attachSavedState(new SavedStateHandle(Collections.singletonMap(BreedExtras.EXTRA_INCUBATION_ID, 6003L)));
        assertEquals(2, (int) vm.getCurrentStep().getValue());
        vm.getFertileCount().setValue("9201");
        vm.submitCandle();
        assertEquals("不能多于入孵蛋数 9,200 枚", TestRules.plain(vm.getFieldErrors().getValue().get("fertile_count")));

        vm.getFertileCount().setValue("8740");
        assertEquals("95.0%（8,740 ÷ 9,200）", vm.getFertilityText().getValue());
        vm.submitCandle();
        assertEquals(3, (int) vm.getCurrentStep().getValue());
        assertEquals(StepItem.DONE, vm.getStepItems().getValue().get(1).getState());
    }

    @Test
    public void step3_hatch_derivedValuesCapacityAndResult() throws Exception {
        when(repo.hatch(eq(6001L), any(HatchRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("hatch_ok_6001.json", RegisterResultVO.class)));
        vm.attachSavedState(new SavedStateHandle(Collections.singletonMap(BreedExtras.EXTRA_INCUBATION_ID, 6001L)));
        assertEquals(3, (int) vm.getCurrentStep().getValue());
        assertEquals("95.0%（9,120 ÷ 9,600）", vm.getFertilityText().getValue());
        StepItem hatch = vm.getStepItems().getValue().get(2);
        assertEquals("预计今天", hatch.getLine1());
        assertEquals("待登记", hatch.getLine2());

        vm.getHatchedCount().setValue("8650");
        vm.getHealthyCount().setValue("8480");
        assertEquals("170", vm.getWeakCount().getValue());
        assertEquals("90.1%", vm.getHatchRateText().getValue());
        assertEquals("不能多于受精蛋数 9,120 枚 · 孵化率 90.1%", TestRules.plain(vm.getHatchedHelper().getValue()));
        assertEquals("8,480 只 · 入栏日龄 0 · 来源自繁", TestRules.plain(vm.getTransferDesc().getValue()));

        // 2 号鸡舍现有 9,300 只、容量 10,000 只：入栏后超容量
        vm.onPenPicked(2202L);
        assertEquals("入栏后超过栏舍容量。2 号鸡舍容量 10,000 只，现有 9,300 只，入栏后共 17,780 只，超出 7,780 只。",
                TestRules.plain(vm.getCapacityText().getValue()));
        vm.submitHatch();
        assertEquals("请先确认超出容量", vm.getFieldErrors().getValue().get("capacity_confirmed"));
        verify(repo, never()).hatch(anyLong(), any(), anyString());

        vm.onPenPicked(2203L);
        assertNull(vm.getCapacity().getValue());
        vm.submitHatch();
        verify(repo).getIncubation(6004L);
        assertEquals(0, (int) vm.getCurrentStep().getValue());
        assertEquals("ZM2610-072", vm.getDetail().getValue().getBatchNo());
    }
}
