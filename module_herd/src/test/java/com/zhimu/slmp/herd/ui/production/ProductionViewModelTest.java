package com.zhimu.slmp.herd.ui.production;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;
import io.reactivex.rxjava3.core.Single;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** L01 三张卡：存栏 / 在养批次（栏舍占用）/ 生产提醒（进入生产提醒页）。 */
public class ProductionViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules schedulers = new TestRules();

    private static ProductionViewModel vmWith(String... overviews) {
        StockRepository repository = mock(StockRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<>());
        if (overviews.length > 0) {
            Single<ProductionOverviewVO> first = Single.just(TestRules.parse(overviews[0], ProductionOverviewVO.class));
            @SuppressWarnings("unchecked")
            Single<ProductionOverviewVO>[] rest = new Single[overviews.length - 1];
            for (int i = 1; i < overviews.length; i++) {
                rest[i - 1] = Single.just(TestRules.parse(overviews[i], ProductionOverviewVO.class));
            }
            when(repository.getOverview()).thenReturn(first, rest);
        }
        return new ProductionViewModel(repository, farm);
    }

    private static <T> T observed(LiveData<T> data) {
        Observer<T> observer = value -> {};
        data.observeForever(observer);
        T value = data.getValue();
        data.removeObserver(observer);
        return value;
    }

    @Test public void batchCardShowsActiveBatchesAndPenOccupancy() {
        ProductionViewModel vm = vmWith(
                "{'stock_total':870,'active_batch_count':4,'pen_count':7,'occupied_pen_count':4,'reminder_total':3}");
        vm.load();
        assertEquals("4", observed(vm.getBatchValueText()));
        assertEquals("4/7", observed(vm.getPenOccupancyText()));
        assertEquals(Integer.valueOf(3), observed(vm.getReminderCount()));
    }

    @Test public void batchCardShowsDashWhenOldServerOmitsNewFields() {
        ProductionViewModel vm = vmWith("{'stock_total':870,'today_todo_count':24,'warning_count':1,'reminder_total':2}");
        vm.load();
        assertEquals(ProductionViewModel.PLACEHOLDER, observed(vm.getBatchValueText()));
        assertNull(observed(vm.getPenOccupancyText()));
        assertEquals(Integer.valueOf(2), observed(vm.getReminderCount()));
    }

    @Test public void reminderCardFollowsReminderTotalOnRefresh() {
        ProductionViewModel vm = vmWith("{'reminder_total':3}", "{'reminder_total':0}");
        Observer<Integer> observer = value -> {};
        vm.getReminderCount().observeForever(observer);
        vm.load();
        assertEquals(Integer.valueOf(3), vm.getReminderCount().getValue());
        vm.refresh();
        assertEquals(Integer.valueOf(0), vm.getReminderCount().getValue());
        vm.getReminderCount().removeObserver(observer);
    }

    @Test public void batchCardOpensBatchArchiveLikeEntry() {
        ProductionViewModel vm = vmWith();
        vm.onMetricClick(ProductionViewModel.METRIC_BATCH);
        NavTarget target = vm.getNavEvent().getValue();
        assertEquals(RouterPath.L03, target.getAction());
        assertEquals(HerdExtras.VIEW_BATCH, target.get(RouterPath.EXTRA_VIEW_TYPE));

        vm.onEntryClick(ProductionViewModel.ENTRY_BATCH_ARCHIVE);
        NavTarget entry = vm.getNavEvent().getValue();
        assertEquals(target.getAction(), entry.getAction());
        assertEquals(target.getExtras(), entry.getExtras());
    }

    @Test public void reminderCardOpensReminderPageEvenWhenEmpty() {
        ProductionViewModel vm = vmWith("{'reminder_total':2}");
        vm.load();
        vm.onMetricClick(ProductionViewModel.METRIC_REMINDER);
        assertEquals(Boolean.TRUE, vm.getOpenReminders().getValue());
        assertNull(vm.getNavEvent().getValue());

        ProductionViewModel empty = vmWith("{'reminder_total':0}");
        empty.load();
        empty.onMetricClick(ProductionViewModel.METRIC_REMINDER);
        assertEquals(Boolean.TRUE, empty.getOpenReminders().getValue());
        assertNull(empty.getNavEvent().getValue());
    }
}
