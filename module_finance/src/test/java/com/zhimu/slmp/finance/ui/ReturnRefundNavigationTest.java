package com.zhimu.slmp.finance.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.finance.TestRules;
import com.zhimu.slmp.finance.data.FinanceRepository;
import com.zhimu.slmp.finance.model.vo.FinanceRecordVO;
import com.zhimu.slmp.finance.ui.recordlist.FinanceRecordListViewModel;
import com.zhimu.slmp.finance.util.NavTarget;
import com.zhimu.slmp.network.GsonFactory;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ReturnRefundNavigationTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();

    @Test public void automaticRefundIncomeOpensItsOriginalReturnWithRefreshOnResult() {
        FinanceRecordListViewModel vm = new FinanceRecordListViewModel(mock(FinanceRepository.class), mock(FarmContext.class));
        FinanceRecordVO income = GsonFactory.get().fromJson("{\"id\":999,\"source_type\":\"DRUG_RETURN_REFUND\","
                + "\"source_target\":{\"biz_type\":\"OUTBOUND\",\"biz_id\":74}}", FinanceRecordVO.class);
        vm.onItemClick(income);
        NavTarget target = vm.getOpenPage().getValue();
        assertNotNull(target);
        assertEquals(RouterPath.STOCK_RECORD, target.getAction());
        assertEquals("OUTBOUND", target.get(RouterPath.EXTRA_RECORD_KIND));
        assertEquals(74L, target.get(RouterPath.EXTRA_RECORD_ID));
        assertTrue(target.isForResult());
    }
}
