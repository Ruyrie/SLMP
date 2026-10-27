package com.zhimu.slmp.herd.ui.production;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;
import io.reactivex.rxjava3.core.Single;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProductionViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules schedulers = new TestRules();

    @Test public void warningCardReachesZeroWhileTwoWithdrawalRemindersRemain() {
        StockRepository repository = mock(StockRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<>());
        when(repository.getOverview()).thenReturn(
                Single.just(TestRules.parse("{'warning_count':1,'reminder_total':3}", ProductionOverviewVO.class)),
                Single.just(TestRules.parse("{'warning_count':0,'reminder_total':2}", ProductionOverviewVO.class)));
        ProductionViewModel vm = new ProductionViewModel(repository, farm);
        Observer<Integer> observer = value -> {};
        vm.getWarningCount().observeForever(observer);
        vm.load();
        assertEquals(Integer.valueOf(1), vm.getWarningCount().getValue());
        vm.refresh();
        assertEquals(Integer.valueOf(0), vm.getWarningCount().getValue());
        assertEquals(2, vm.getOverview().getValue().getReminderTotal());
        vm.getWarningCount().removeObserver(observer);
    }

    @Test public void warningClickOpensAllUnresolvedWarningsWithoutCalendarDateFilter() {
        FarmContext farm = mock(FarmContext.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<>());
        ProductionViewModel vm = new ProductionViewModel(mock(StockRepository.class), farm);
        vm.onMetricClick(ProductionViewModel.METRIC_WARNING);
        NavTarget target = vm.getNavEvent().getValue();
        assertEquals(RouterPath.T09, target.getAction());
        assertEquals("WARNING", target.get("filter"));
        assertNull(target.get(RouterPath.EXTRA_DATE));
        assertNull(target.get(RouterPath.EXTRA_MODE));
    }
}
