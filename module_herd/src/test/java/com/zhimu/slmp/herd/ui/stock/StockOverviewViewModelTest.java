package com.zhimu.slmp.herd.ui.stock;

import android.app.Activity;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.model.vo.CategoryStockVO;
import com.zhimu.slmp.herd.model.vo.StockSummaryVO;
import org.junit.Rule;
import org.junit.Test;
import java.util.Collections;
import io.reactivex.rxjava3.core.Single;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class StockOverviewViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules schedulers = new TestRules();

    @Test public void returningThroughBatchDetailsRefreshesEvenWithoutPropagatedSuccessResult() {
        StockRepository stock = mock(StockRepository.class);
        when(stock.getStockSummary()).thenReturn(
                Single.just(TestRules.parse("{'total_stock':670,'active_batch_count':3}", StockSummaryVO.class)),
                Single.just(TestRules.parse("{'total_stock':870,'active_batch_count':4}", StockSummaryVO.class)));
        when(stock.getCategoryStocks(anyString())).thenReturn(
                Single.just(Collections.singletonList(TestRules.parse("{'category_id':1,'stock':670}", CategoryStockVO.class))),
                Single.just(Collections.singletonList(TestRules.parse("{'category_id':1,'stock':870}", CategoryStockVO.class))));
        StockOverviewViewModel vm = new StockOverviewViewModel(stock, mock(BatchRepository.class));
        vm.setViewType(HerdExtras.VIEW_CATEGORY);
        vm.loadSummary();
        assertEquals(670, vm.getSummary().getValue().getTotalStock());
        // 入栏成功后继续浏览子页面，系统返回码可能仍为取消。
        vm.onChildResult(Activity.RESULT_CANCELED, null);
        assertEquals(870, vm.getSummary().getValue().getTotalStock());
        assertEquals(4, vm.getSummary().getValue().getActiveBatchCount());
        assertEquals(870, vm.getCategories().getValue().get(0).getStock());
        verify(stock, times(2)).getStockSummary();
        verify(stock, times(2)).getCategoryStocks(anyString());
    }
}
