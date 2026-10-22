package com.zhimu.slmp.farm.ui.switcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.RxSchedulersRule;
import com.zhimu.slmp.farm.TestData;
import com.zhimu.slmp.farm.repository.FarmRepository;
import com.zhimu.slmp.farm.service.FarmContextServiceImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Single;

/**
 * FarmSwitchViewModel：点已通过的养殖场调 API-F03；待审核 / 已驳回进 F03；冻结进 U03；“全部养殖场”只在允许时可选。
 */
public class FarmSwitchViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxSchedulersRule rx = new RxSchedulersRule();

    private FarmContextServiceImpl contextService;
    private FarmContext farmContext;
    private FarmSwitchViewModel vm;

    private final FarmContextVO ctx1001 = TestData.context(1001, "东山猪场", "OWNER", "NORMAL");
    private final FarmContextVO ctx1002 = TestData.context(1002, "西岭鸡场", "MANAGER", "NORMAL");

    @Before
    public void setUp() {
        contextService = mock(FarmContextServiceImpl.class);
        farmContext = mock(FarmContext.class);
        when(farmContext.current()).thenReturn(ctx1001);
        vm = new FarmSwitchViewModel(mock(FarmRepository.class), contextService, farmContext);
        vm.attachSavedState(new SavedStateHandle());
        vm.setup(false, false);
    }

    @Test
    public void clickNormalFarm_switchesAndEmitsSwitched() {
        when(contextService.switchTo(1002)).thenReturn(Single.just(ctx1002));

        vm.onFarmClick(TestData.item(1002, "西岭鸡场", "NORMAL", true, false));

        verify(contextService).switchTo(1002);
        assertSame(ctx1002, vm.getSwitched().getValue());
        assertNull(vm.getSwitchingFarmId().getValue());
        assertNull(vm.getMessage().getValue());
    }

    @Test
    public void clickPendingFarm_opensAuditWithoutSwitching() {
        vm.onFarmClick(TestData.item(1003, "北湾牛场", "PENDING", false, false));

        assertEquals(Long.valueOf(1003), vm.getOpenAudit().getValue());
        verify(contextService, never()).switchTo(anyLong());
    }

    @Test
    public void clickRejectedFarm_opensAudit() {
        vm.onFarmClick(TestData.item(1003, "北湾牛场", "REJECTED", false, false));

        assertEquals(Long.valueOf(1003), vm.getOpenAudit().getValue());
    }

    @Test
    public void clickFrozenFarm_opensFrozenStatus() {
        vm.onFarmClick(TestData.item(1002, "西岭鸡场", "FROZEN", false, false));

        assertEquals("西岭鸡场已被平台冻结，暂时不能使用。原因见消息中心。", vm.getOpenFrozen().getValue());
        verify(contextService, never()).switchTo(anyLong());
    }

    @Test
    public void clickCurrentFarm_closesWithoutRequest() {
        vm.onFarmClick(TestData.item(1001, "东山猪场", "NORMAL", true, true));

        assertSame(ctx1001, vm.getSwitched().getValue());
        verify(contextService, never()).switchTo(anyLong());
    }

    @Test
    public void switchReturns40305_opensAudit() {
        when(contextService.switchTo(1002)).thenReturn(Single.error(
                new ApiException(ErrorCodes.FARM_STATUS_FORBIDDEN, "西岭鸡场还在审核中，审核通过后才能切换")));

        vm.onFarmClick(TestData.item(1002, "西岭鸡场", "NORMAL", true, false));

        assertEquals(Long.valueOf(1002), vm.getOpenAudit().getValue());
        assertNull(vm.getSwitchingFarmId().getValue());
    }

    @Test
    public void switchFallsBackToAnotherFarm_showsNotMemberMessage() {
        when(contextService.switchTo(1002)).thenReturn(Single.just(ctx1001));

        vm.onFarmClick(TestData.item(1002, "西岭鸡场", "NORMAL", true, false));

        assertEquals("你已不是西岭鸡场的成员", vm.getMessage().getValue().getText());
        assertSame(ctx1001, vm.getSwitched().getValue());
    }

    @Test
    public void allFarms_onlyWhenAllowed() {
        AtomicInteger chosen = new AtomicInteger();
        vm.getAllFarmsChosen().observeForever(v -> chosen.incrementAndGet());

        vm.onAllFarmsClick();
        assertEquals(0, chosen.get());

        vm.setup(true, false);
        vm.onAllFarmsClick();
        assertEquals(1, chosen.get());
        assertTrue(vm.getAllFarmsChecked().getValue());
    }

    @Test
    public void createClick_emitsOpenCreate() {
        AtomicInteger opened = new AtomicInteger();
        vm.getOpenCreate().observeForever(v -> opened.incrementAndGet());

        vm.onCreateClick();

        assertEquals(1, opened.get());
    }
}
