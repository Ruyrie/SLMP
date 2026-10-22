package com.zhimu.slmp.breed.ui.mating;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.zhimu.slmp.breed.model.BreedCodes;
import com.zhimu.slmp.breed.model.request.MatingRequest;
import com.zhimu.slmp.breed.model.request.PregnancyCheckRequest;
import com.zhimu.slmp.breed.model.vo.BreedingAnimalOptionVO;
import com.zhimu.slmp.breed.model.vo.PendingCheckVO;
import com.zhimu.slmp.breed.model.vo.RegisterContextVO;
import com.zhimu.slmp.breed.model.vo.RegisterResultVO;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.network.model.PageVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * B03 登记配种 / 妊检：预产期推算卡、配种表单校验、提交成功提示，以及“登记妊检”面板的校验与提交（数据取本组件 Mock）。
 */
public class MatingRegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private BreedRepository repo;
    private MatingRegisterViewModel vm;

    @Before
    public void setUp() throws Exception {
        repo = mock(BreedRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        PageVO<BreedingAnimalOptionVO> options = TestRules.data("options_mating.json",
                new TypeToken<PageVO<BreedingAnimalOptionVO>>() { }.getType());
        PageVO<PendingCheckVO> checks = TestRules.data("pending_checks.json",
                new TypeToken<PageVO<PendingCheckVO>>() { }.getType());
        when(repo.getOptions(any())).thenReturn(Single.just(options));
        when(repo.getPendingChecks()).thenReturn(Single.just(checks));
        when(repo.getRegisterContext(4161L, BreedCodes.TYPE_MATING)).thenReturn(Single.just(
                TestRules.data("ctx_mating_0161.json", RegisterContextVO.class)));
        when(repo.getRegisterContext(4155L, BreedCodes.TYPE_MATING)).thenReturn(Single.just(
                TestRules.data("ctx_mating_0155.json", RegisterContextVO.class)));
        vm = new MatingRegisterViewModel(repo, farm);
        vm.getExpectedDueDate().observeForever(v -> { });
        vm.getDueNote().observeForever(v -> { });
        vm.getCanSubmit().observeForever(v -> { });
    }

    private void open(Long animalId) {
        Map<String, Object> args = animalId == null ? Collections.emptyMap()
                : Collections.singletonMap(BreedExtras.EXTRA_ANIMAL_ID, animalId);
        vm.attachSavedState(new SavedStateHandle(args));
    }

    @Test
    public void projection_emptySow_matingDatePlusGestation() {
        open(4161L);
        assertEquals("0161", vm.getSelectedAnimal().getValue().getEarTag());
        assertEquals("2027-02-10", vm.getExpectedDueDate().getValue());
        assertEquals("按长大二元妊娠期 114 天推算；同一情期再次配种时，仍按首次配种日期计算", vm.getDueNote().getValue());
        assertTrue(vm.getCanSubmit().getValue());
        assertEquals(2, (int) vm.getPendingTotal().getValue());
    }

    @Test
    public void projection_reMating_keepsFirstMatingDate() {
        open(4155L);
        vm.onMatingDatePicked("2026-10-18");
        assertEquals("2027-01-16", vm.getExpectedDueDate().getValue());
    }

    @Test
    public void submit_validatesBeforeRequest() {
        open(null);
        vm.submitMating();
        assertEquals("请选择母猪", vm.getFieldErrors().getValue().get("animal_id"));

        vm.selectAnimal(4161L);
        vm.onMatingDatePicked("2026-10-10");
        vm.submitMating();
        assertEquals("配种日期不能早于上次断奶日期 10月12日", vm.getFieldErrors().getValue().get("mating_date"));
        verify(repo, never()).createMating(any(), anyString());
    }

    @Test
    public void submit_success_finishesWithDueDate() throws Exception {
        when(repo.createMating(any(MatingRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("mating_0161.json", RegisterResultVO.class)));
        open(4161L);
        vm.submitMating();
        assertEquals("已登记配种，预产期 2027年2月10日 周三", vm.getFinishWithMessage().getValue());
        assertFalse(vm.getSubmitting().getValue());
        assertNotNull(vm.currentIdemKey());
    }

    @Test
    public void pregCheck_requiresResult_thenRemovesFromPending() throws Exception {
        when(repo.createPregCheck(any(PregnancyCheckRequest.class), anyString())).thenReturn(Single.just(
                TestRules.data("check_0155_positive.json", RegisterResultVO.class)));
        open(null);
        PendingCheckVO first = vm.getPendingChecks().getValue().get(0);
        vm.openCheck(first);
        assertEquals("0155 · 长大二元 · 第 3 胎 · 9月24日配种，已 25 天", vm.getCheckSubtitle().getValue());
        assertEquals("转为妊娠，预产期 2027年1月16日 周六", vm.getPositiveHint().getValue());

        vm.submitCheck();
        assertEquals("请选择妊检结果", vm.getCheckErrors().getValue().get("result"));
        verify(repo, never()).createPregCheck(any(), anyString());

        vm.onCheckResultPicked(BreedCodes.POSITIVE);
        vm.submitCheck();
        verify(repo).createPregCheck(any(PregnancyCheckRequest.class), anyString());
        assertEquals("已登记妊检，0155 转为妊娠", vm.getDismissCheckSheet().getValue());
        assertEquals(1, (int) vm.getPendingTotal().getValue());
        assertEquals(1, vm.getPendingChecks().getValue().size());
        // 阴性回到空怀的母猪会重新出现在配种候选中：妊检后重新取 API-B04
        verify(repo, org.mockito.Mockito.times(2)).getOptions(any());
    }
}
