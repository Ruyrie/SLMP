package com.zhimu.slmp.stock.ui;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.vo.StockInboundVO;
import com.zhimu.slmp.stock.model.vo.StockOutboundVO;
import com.zhimu.slmp.stock.ui.record.StockRecordViewModel;
import org.junit.Rule;
import org.junit.Test;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StockRecordViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();

    @Test public void inboundAndOutboundUseTheirOwnReadOnlyDetailsAndBusinessTypes() {
        StockRepository repo = mock(StockRepository.class);
        StockInboundVO inbound = new StockInboundVO();
        inbound.inboundNo = "RK-73";
        inbound.materialName = "阿莫西林";
        inbound.amount = new BigDecimal("90.00");
        when(repo.getInbound(73)).thenReturn(Single.just(inbound));
        StockRecordViewModel vm = new StockRecordViewModel(repo);
        vm.load("INBOUND", 73);
        assertEquals("入库单详情", vm.getTitle().getValue());
        assertEquals("INBOUND", vm.bizType());
        assertTrue(vm.getRows().getValue().stream().anyMatch(row -> "阿莫西林".equals(row[1])));
        StockOutboundVO outbound = new StockOutboundVO();
        outbound.outboundNo = "CK-74";
        outbound.returnTo = "供应商";
        outbound.materialType = "DRUG";
        outbound.outType = "RETURN";
        outbound.outTypeText = "退货";
        when(repo.getOutbound(74)).thenReturn(Single.just(outbound));
        StockRecordViewModel other = new StockRecordViewModel(repo);
        other.load("OUTBOUND", 74);
        assertEquals("OUTBOUND", other.bizType());
        assertEquals("退货单详情", other.getTitle().getValue());
        assertSame(outbound, other.getOutbound().getValue());
        assertTrue(other.getRows().getValue().stream().anyMatch(row -> "供应商".equals(row[1])));
        verify(repo, never()).getLoss(anyLong());
        verify(repo, never()).getStocktake(anyLong());
    }
}
