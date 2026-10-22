package com.zhimu.slmp.feed.ui.weigh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.TestRules;
import com.zhimu.slmp.feed.data.WeighRepository;
import com.zhimu.slmp.feed.model.request.WeighRecordCreateRequest;
import com.zhimu.slmp.feed.model.vo.WeighFormVO;
import com.zhimu.slmp.feed.model.vo.WeighRecordVO;
import com.zhimu.slmp.feed.util.GrowthCalc;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * G05 称重偏离计算：抽样 20 头 1,200 千克 → 平均 60.0、标准 68.6（96 日龄插值）、偏离 −12.5% 生长偏慢、日增重 0.81、
 * 抽样偏少；超过存栏禁用提交；逐头录入合计与平均；只提交当前方式的字段。
 */
public class WeighRegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private WeighRepository repo;
    private WeighRegisterViewModel vm;

    @Before
    public void setUp() throws IOException {
        repo = mock(WeighRepository.class);
        FarmContext farm = mock(FarmContext.class);
        AppDatabase db = mock(AppDatabase.class);
        FormDraftDao dao = mock(FormDraftDao.class);
        when(db.formDraftDao()).thenReturn(dao);
        when(dao.get(anyString(), anyLong(), anyString())).thenReturn(Maybe.empty());
        when(dao.delete(anyString(), anyLong(), anyString())).thenReturn(Completable.complete());
        when(dao.deleteOlderThan(anyLong())).thenReturn(Completable.complete());
        when(farm.farmIdOrZero()).thenReturn(1001L);
        when(repo.getWeighForm(eq(3001L), isNull())).thenReturn(Single.just(TestRules.mock("weigh_form_3001.json",
                WeighFormVO.class)));
        vm = new WeighRegisterViewModel(repo, farm, db);
        vm.getCanSubmit().observeForever(v -> {
        });
        Map<String, Object> args = new HashMap<>();
        args.put(RouterPath.EXTRA_BATCH_ID, 3001L);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    @Test
    public void sample20Total1200_avg60_deviationMinus12point5_slow() {
        assertEquals("ZM2610-031 · 3 号育肥舍", vm.getBatchCardTitle().getValue());
        assertEquals("上次称重 10月5日 · 平均 48.6 千克", TestRules.plain(vm.getLastWeighText().getValue()));
        vm.getSampleCount().setValue("20");
        vm.getTotalWeight().setValue("1,200");

        assertEquals("60.0", vm.getAvgText().getValue());
        assertEquals("1,200 千克 ÷ 20 头", TestRules.plain(vm.getFormulaText().getValue()));
        assertEquals("标准体重（96 日龄）", vm.getStdLabel().getValue());
        assertEquals("68.6 千克", TestRules.plain(vm.getStdText().getValue()));
        assertEquals("-12.5%（允许 ±10%）", TestRules.plain(vm.getDeviationText().getValue()));
        assertTrue(vm.getDeviationError().getValue());
        GrowthPreview p = vm.getGrowthPreview().getValue();
        assertEquals(GrowthCalc.SLOW, p.status);
        assertEquals("生长偏慢", p.tagText);
        assertEquals("提交后批次标为生长偏慢，下次称重回到范围内自动取消", p.note);
        assertEquals("日增重（与 10月5日 比）", vm.getDailyGainLabel().getValue());
        assertEquals("0.81 千克", TestRules.plain(vm.getDailyGainText().getValue()));
        assertTrue(vm.getSampleLow().getValue());
        assertEquals("建议至少抽 30 头（存栏的 15%），否则平均体重可能不准。仍可以保存。",
                TestRules.plain(vm.getSampleLowText().getValue()));
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void sampleOverStock_disablesSubmit() {
        vm.getSampleCount().setValue("201");
        vm.getTotalWeight().setValue("12000");
        assertEquals("不能超过存栏 200 头", TestRules.plain(vm.getFieldErrors().getValue()
                .get(WeighRegisterViewModel.F_SAMPLE)));
        assertFalse(vm.getCanSubmit().getValue());
        vm.getSampleCount().setValue("40");
        assertNull(vm.getFieldErrors().getValue().get(WeighRegisterViewModel.F_SAMPLE));
        assertFalse(vm.getSampleLow().getValue());
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void emptyFields_validationMessages() {
        vm.submit();
        assertEquals("请填写抽样数", vm.getFieldErrors().getValue().get(WeighRegisterViewModel.F_SAMPLE));
        assertEquals("请填写总重", vm.getFieldErrors().getValue().get(WeighRegisterViewModel.F_TOTAL));
        vm.selectInputMode(WeighRegisterViewModel.MODE_INDIVIDUAL);
        vm.addItemRow();
        vm.setItemWeight(0, "60.5");
        vm.submit();
        assertEquals("请填写第 2 头的体重", vm.getFieldErrors().getValue().get(WeighRegisterViewModel.itemKey(1)));
        verify(repo, never()).createWeighRecord(anyLong(), any(), anyString());
    }

    @Test
    public void individualRows_sumAndAverage_submitOnlyItems() throws IOException {
        vm.getSampleCount().setValue("20");
        vm.getTotalWeight().setValue("1200");
        vm.selectInputMode(WeighRegisterViewModel.MODE_INDIVIDUAL);
        vm.setItemWeight(0, "60.5");
        vm.addItemRow();
        vm.setItemWeight(1, "58.0");
        vm.addItemRow();
        vm.setItemWeight(2, "63.0");
        assertEquals("已录 3 头 · 合计 181.5 千克", TestRules.plain(vm.getIndividualSummary().getValue()));
        assertEquals("合计 181.5 千克 ÷ 3 头", TestRules.plain(vm.getFormulaText().getValue()));
        assertEquals("60.5", vm.getAvgText().getValue());

        WeighRecordVO saved = TestRules.mock("weigh_created.json", WeighRecordVO.class);
        when(repo.createWeighRecord(eq(3001L), any(), anyString()))
                .thenReturn(Single.just(new ApiResponse<>(0, "已登记称重，批次已标为生长偏慢", saved, "t")));
        vm.submit();
        ArgumentCaptor<WeighRecordCreateRequest> req = ArgumentCaptor.forClass(WeighRecordCreateRequest.class);
        verify(repo).createWeighRecord(eq(3001L), req.capture(), anyString());
        assertEquals(WeighRegisterViewModel.MODE_INDIVIDUAL, req.getValue().mode);
        assertEquals(3, req.getValue().items.size());
        assertNull(req.getValue().sampleCount);
        assertNull(req.getValue().totalWeight);
        assertEquals("2026-10-19", req.getValue().weighDate);
        assertEquals("已登记称重，批次已标为生长偏慢", vm.getNavigateToGrowth().getValue().getText());
    }

    @Test
    public void successMessageByGrowthChange() {
        assertEquals("已登记称重，批次已标为生长偏慢", WeighRegisterViewModel.successMessage("SET_SLOW"));
        assertEquals("已登记称重，生长偏慢标记已取消", WeighRegisterViewModel.successMessage("CLEARED"));
        assertEquals("已登记称重，待办已自动完成", WeighRegisterViewModel.successMessage("UNCHANGED"));
    }
}
