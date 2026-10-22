package com.zhimu.slmp.farm.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.RxSchedulersRule;
import com.zhimu.slmp.farm.TestData;
import com.zhimu.slmp.farm.repository.FarmRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;

/**
 * FarmContextServiceImpl：切换成功写入 FarmContext；40302、41001 或 farmId 无效时按 2.6.1 兜底重定当前养殖场。
 */
public class FarmContextServiceImplTest {

    @Rule
    public RxSchedulersRule rx = new RxSchedulersRule();

    private FarmRepository repository;
    private FarmContext farmContext;
    private FarmContextServiceImpl service;

    private final FarmContextVO ctx1001 = TestData.context(1001, "东山猪场", "OWNER", "NORMAL");
    private final FarmContextVO ctx1002 = TestData.context(1002, "西岭鸡场", "MANAGER", "NORMAL");
    private final FarmContextVO ctx1003 = TestData.context(1003, "北湾牛场", "OWNER", "PENDING");

    @Before
    public void setUp() {
        repository = mock(FarmRepository.class);
        farmContext = mock(FarmContext.class);
        service = new FarmContextServiceImpl(repository, farmContext);
    }

    @Test
    public void switchTo_success_updatesContext() {
        when(repository.switchFarm(1002)).thenReturn(Single.just(ctx1002));

        TestObserver<FarmContextVO> to = service.switchTo(1002).test();

        to.assertValue(ctx1002);
        verify(farmContext).update(ctx1002);
        verify(repository, never()).getFarms(anyString(), anyInt(), anyInt());
    }

    @Test
    public void switchTo_notMember_switchesToFirstSwitchableFarm() {
        when(repository.switchFarm(1002)).thenReturn(Single.error(new ApiException(ErrorCodes.NOT_FARM_MEMBER, "你已不是该养殖场的成员")));
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page(
                TestData.item(1002, "西岭鸡场", "NORMAL", true, false),
                TestData.item(1003, "北湾牛场", "PENDING", false, false),
                TestData.item(1001, "东山猪场", "NORMAL", true, true))));
        when(repository.switchFarm(1001)).thenReturn(Single.just(ctx1001));

        TestObserver<FarmContextVO> to = service.switchTo(1002).test();

        // 跳过失败的 1002，取第一个 can_switch 的 1001 再调 API-F03
        to.assertValue(ctx1001);
        verify(farmContext).update(ctx1001);
        verify(farmContext, never()).clear();
    }

    @Test
    public void switchTo_deleted_onlyPendingFarm_usesContextOfPendingFarm() {
        when(repository.switchFarm(1001)).thenReturn(Single.error(new ApiException(ErrorCodes.RECORD_DELETED, "已删除")));
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page(
                TestData.item(1003, "北湾牛场", "PENDING", false, false))));
        when(repository.getContext(1003)).thenReturn(Single.just(ctx1003));

        TestObserver<FarmContextVO> to = service.switchTo(1001).test();

        // 只有待审核养殖场时用 API-F02 作为当前养殖场
        to.assertValue(ctx1003);
        verify(repository).getContext(1003);
        verify(farmContext).update(ctx1003);
    }

    @Test
    public void switchTo_notMember_noFarmLeft_clearsContextAndFails() {
        when(repository.switchFarm(1001)).thenReturn(Single.error(new ApiException(ErrorCodes.NOT_FARM_MEMBER, "你已不是该养殖场的成员")));
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page()));

        TestObserver<FarmContextVO> to = service.switchTo(1001).test();

        to.assertError(t -> t instanceof ApiException && ((ApiException) t).getCode() == ErrorCodes.NOT_FARM_MEMBER);
        verify(farmContext).clear();
        verify(farmContext, never()).update(any());
    }

    @Test
    public void switchTo_networkError_noFallback() {
        when(repository.switchFarm(1002)).thenReturn(Single.error(ApiException.network(new IOException("offline"))));

        TestObserver<FarmContextVO> to = service.switchTo(1002).test();

        to.assertError(ApiException.class);
        verify(repository, never()).getFarms(anyString(), anyInt(), anyInt());
        verify(farmContext, never()).update(any());
        verify(farmContext, never()).clear();
    }

    @Test
    public void switchTo_invalidFarmId_selectsFirstFarm() {
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page(
                TestData.item(1001, "东山猪场", "NORMAL", true, true))));
        when(repository.switchFarm(1001)).thenReturn(Single.just(ctx1001));

        TestObserver<FarmContextVO> to = service.switchTo(0).test();

        to.assertValue(ctx1001);
        verify(farmContext).update(ctx1001);
    }

    @Test
    public void refreshContext_currentFarm_updatesFromF02() {
        when(farmContext.farmIdOrZero()).thenReturn(1001L);
        when(repository.getContext(1001)).thenReturn(Single.just(ctx1001));

        service.refreshContext().test().assertComplete();

        verify(farmContext).update(ctx1001);
        verify(repository, never()).switchFarm(anyLong());
    }

    @Test
    public void refreshContext_deletedFarm_fallsBack() {
        when(farmContext.farmIdOrZero()).thenReturn(1003L);
        when(repository.getContext(1003)).thenReturn(Single.error(new ApiException(ErrorCodes.RECORD_DELETED, "北湾牛场已被删除")));
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page(
                TestData.item(1002, "西岭鸡场", "NORMAL", true, false))));
        when(repository.switchFarm(1002)).thenReturn(Single.just(ctx1002));

        service.refreshContext().test().assertComplete();

        verify(farmContext).update(ctx1002);
    }

    @Test
    public void selectNext_skipsExcludedFarm() {
        when(repository.getFarms(anyString(), anyInt(), anyInt())).thenReturn(Single.just(TestData.page(
                TestData.item(1001, "东山猪场", "NORMAL", true, true),
                TestData.item(1002, "西岭鸡场", "NORMAL", true, false))));
        when(repository.switchFarm(1002)).thenReturn(Single.just(ctx1002));

        service.selectNext(1001).test().assertValue(opt -> opt.isPresent() && opt.get() == ctx1002);
        assertEquals(1002, ctx1002.getFarmId());
        verify(repository, never()).switchFarm(1001);
    }
}
