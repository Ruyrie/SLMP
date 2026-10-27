package com.zhimu.slmp.account.change;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.ChangeRequestVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.ui.change.ChangeRecordRoute;
import com.zhimu.slmp.common.ui.change.ChangeRepository;
import com.zhimu.slmp.common.ui.change.MyChangeRequestsViewModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MyChangeRequestsViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public RxTestRule rx = new RxTestRule();
    private IFarmContextService service;
    private ChangeRepository repository;
    private MyChangeRequestsViewModel vm;

    @Before public void setup() {
        FarmContext farm = mock(FarmContext.class);
        when(farm.currentFarmId()).thenReturn(1001L);
        service = mock(IFarmContextService.class);
        ServiceHub.register(IFarmContextService.class, service);
        repository = mock(ChangeRepository.class);
        vm = new MyChangeRequestsViewModel(repository, farm);
    }

    private ChangeRequestVO item(String type, String status) {
        return TestData.fromJson("{\"id\":42,\"biz_id\":73,\"farm_id\":1002,\"biz_type\":\"" + type
                + "\",\"status\":\"" + status + "\"}", ChangeRequestVO.class);
    }

    @Test public void resubmitWaitsForFarmSwitchAndDoesNotOpenOnFailure() {
        SingleSubject<FarmContextVO> switching = SingleSubject.create();
        when(service.switchTo(1002L)).thenReturn(switching);
        vm.onResubmitClick(item("LOSS", "REJECTED"));
        assertNull(vm.getOpenResubmit().getValue());
        switching.onError(ApiException.network(null));
        assertNull(vm.getOpenResubmit().getValue());
        when(service.switchTo(1002L)).thenReturn(Single.just(TestData.fromJson("{\"farm_id\":1002,\"name\":\"测试场\"}", FarmContextVO.class)));
        vm.onResubmitClick(item("LOSS", "REJECTED"));
        assertEquals(Long.valueOf(42), vm.getOpenResubmit().getValue());
    }

    @Test public void pendingRequestCannotResubmitAndStockRoutesKeepRecordIdentity() {
        vm.onResubmitClick(item("LOSS", "PENDING"));
        verifyNoInteractions(service);
        for (String type : new String[]{"LOSS", "STOCKTAKE", "INBOUND", "OUTBOUND"}) {
            ChangeRequestVO record = item(type, "APPROVED");
            assertEquals(RouterPath.STOCK_RECORD, ChangeRecordRoute.action(record));
            assertEquals(type, ChangeRecordRoute.arguments(record).get(RouterPath.EXTRA_RECORD_KIND));
            assertEquals(73L, ChangeRecordRoute.arguments(record).get(RouterPath.EXTRA_RECORD_ID));
            assertEquals(1002L, ChangeRecordRoute.arguments(record).get(RouterPath.EXTRA_FARM_ID));
        }
        assertEquals(RouterPath.E07, ChangeRecordRoute.action(item("SALE_ORDER", "APPROVED")));
        assertEquals(73L, ChangeRecordRoute.arguments(item("SALE_ORDER", "APPROVED")).get(RouterPath.EXTRA_SALE_ORDER_ID));
    }

    @Test public void unsupportedRecordAndLegacyEntryNeverOpenANewRegistration() {
        ChangeRequestVO entry = item("BATCH_ENTRY", "APPROVED");
        assertNull(ChangeRecordRoute.action(entry));
        ChangeRequestVO exit = item("BREEDING_EXIT", "REJECTED");
        assertNull(ChangeRecordRoute.action(exit));
        when(repository.getForm("BREEDING_EXIT", 73)).thenReturn(Single.just(TestData.fromJson(
                "{\"biz_type\":\"BREEDING_EXIT\",\"biz_id\":73,\"record_title\":\"种畜死亡淘汰\"}",
                com.zhimu.slmp.common.model.vo.ChangeRequestFormVO.class)));
        vm.loadOriginalRecord(exit);
        assertEquals(73L, vm.getOriginalRecord().getValue().getBizId());
        verify(repository, never()).submit(any(), anyString());
    }
}
