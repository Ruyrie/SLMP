package com.zhimu.slmp.stock.ui.stocktake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.JsonParser;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.request.StocktakeConfirmRequest;
import com.zhimu.slmp.stock.model.vo.StockLossVO;
import com.zhimu.slmp.stock.model.vo.StocktakeSheetVO;
import com.zhimu.slmp.stock.model.vo.StocktakeVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;

import io.reactivex.rxjava3.core.Single;

/**
 * M04 盘点与报损：盘盈盘亏与按均价折合、账实相符；只提交已录入的饲料、未录入时提示；确认对话框正文；
 * 40902 账面已变化时 bookChanged，“刷新”用 data.items 更新账面与版本并保留实盘；报损超结存、原因必选与“其他”需说明；
 * 报损成功后重取盘点表并保留实盘。
 */
public class StocktakeViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private StockRepository repo;
    private StocktakeViewModel vm;

    @Before
    public void setUp() throws IOException {
        repo = mock(StockRepository.class);
        FarmContext farm = mock(FarmContext.class);
        when(farm.farmIdOrZero()).thenReturn(1001L);
        when(repo.getStocktakeSheet()).thenAnswer(inv -> Single.just(TestRules.mock("stocktake_sheet.json",
                StocktakeSheetVO.class)));
        vm = new StocktakeViewModel(repo, TestRules.noDrafts(), farm);
        vm.attachSavedState(new SavedStateHandle());
    }

    @Test
    public void diff_gainLossMatch() throws IOException {
        StocktakeSheetVO s = TestRules.mock("stocktake_sheet.json", StocktakeSheetVO.class);
        StocktakeViewModel.Diff loss = StocktakeViewModel.diff(s.items.get(0), "488");
        assertEquals("盘亏 12 千克", TestRules.plain(loss.qtyText));
        assertEquals("按均价折合 38.40 元", loss.amountText);
        StocktakeViewModel.Diff gain = StocktakeViewModel.diff(s.items.get(1), "1856");
        assertEquals("盘盈 6 千克", TestRules.plain(gain.qtyText));
        assertEquals("按均价折合 25.20 元", gain.amountText);
        StocktakeViewModel.Diff match = StocktakeViewModel.diff(s.items.get(0), "500");
        assertEquals("账实相符", match.qtyText);
        assertNull(match.amountText);
        assertNull(StocktakeViewModel.diff(s.items.get(0), ""));
    }

    @Test
    public void confirm_withoutActual_showsMessage() {
        vm.confirm();
        assertEquals(StocktakeViewModel.MSG_EMPTY, vm.getMessage().getValue().getText());
        assertNull(vm.getShowConfirm().getValue());
    }

    @Test
    public void negativeActual_blocksConfirm() {
        vm.onActualChanged(5101L, "-1");
        assertEquals("实盘数量不能小于 0", vm.getActualErrors().getValue().get(5101L));
        vm.confirm();
        assertNull(vm.getShowConfirm().getValue());
    }

    @Test
    public void confirm_onlyEnteredFeeds_andDialogText() {
        vm.onActualChanged(5101L, "488");
        vm.onActualChanged(5102L, "1856");
        vm.confirm();
        assertEquals("将按实盘数量更新 2 种饲料的结存，生成盘亏 1 条、盘盈 1 条记录。确认后如需更改，要提交修改申请。",
                vm.getShowConfirm().getValue());
        vm.onActualChanged(5102L, "");
        assertEquals(1, vm.buildConfirmRequest().items.size());
        assertEquals(Integer.valueOf(12), vm.buildConfirmRequest().items.get(0).stockVersion);
    }

    @Test
    public void bookChanged_refreshUsesDataItemsAndKeepsActual() throws IOException {
        vm.onActualChanged(5101L, "488");
        ApiException e = new ApiException(ErrorCodes.VERSION_CONFLICT, "账面结存已变化，请刷新后重新核对", "t",
                JsonParser.parseString("{\"items\":[{\"feed_id\":5101,\"book_qty\":490,\"stock_version\":13}]}"),
                null, null);
        when(repo.confirmStocktake(any(), anyString())).thenReturn(Single.error(e));
        vm.doConfirm();
        assertEquals(StocktakeViewModel.MSG_BOOK_CHANGED, vm.getBookChanged().getValue());
        vm.onBookRefresh();
        StocktakeSheetVO.Item item = vm.getSheet().getValue().items.get(0);
        assertEquals(0, item.bookQty.compareTo(new BigDecimal("490")));
        assertEquals(Integer.valueOf(13), item.stockVersion);
        assertEquals("488", vm.getActualQty().getValue().get(5101L));
        assertEquals("盘亏 2 千克", TestRules.plain(vm.getDiffs().getValue().get(5101L).qtyText));
    }

    @Test
    public void confirm_success() throws IOException {
        vm.onActualChanged(5101L, "488");
        when(repo.confirmStocktake(any(), anyString())).thenReturn(Single.just(TestRules.mock("stocktake_created.json",
                StocktakeVO.class)));
        vm.doConfirm();
        assertEquals("已确认盘点，结存已更新", vm.getStocktakeDone().getValue());
        ArgumentCaptor<StocktakeConfirmRequest> req = ArgumentCaptor.forClass(StocktakeConfirmRequest.class);
        verify(repo).confirmStocktake(req.capture(), anyString());
        assertEquals(0, req.getValue().items.get(0).actualQty.compareTo(new BigDecimal("488")));
    }

    @Test
    public void loss_validation() {
        vm.openLoss(5101L);
        assertEquals("登记报损 · 育肥全价料", vm.lossTitle());
        assertEquals("当前结存 500 千克 · 按今天登记", TestRules.plain(vm.lossSubtitle()));
        vm.onLossQtyChanged("600");
        assertEquals("超过当前结存 500 千克，出库后库存不能为负数",
                TestRules.plain(vm.getLossErrors().getValue().get(StocktakeViewModel.F_LOSS_QTY)));
        vm.submitLoss();
        assertEquals("请选择报损原因", vm.getLossErrors().getValue().get(StocktakeViewModel.F_LOSS_REASON));
        vm.onLossQtyChanged("10");
        vm.onLossReasonPicked("OTHER");
        vm.submitLoss();
        assertEquals("请写明报损原因", vm.getLossErrors().getValue().get(StocktakeViewModel.F_LOSS_REMARK));
        verify(repo, never()).createFeedLoss(any(), anyString());
    }

    @Test
    public void loss_success_reloadsSheetAndKeepsActual() throws IOException {
        vm.onActualChanged(5102L, "1856");
        vm.openLoss(5101L);
        vm.onLossQtyChanged("10");
        vm.onLossReasonPicked("MILDEW");
        when(repo.createFeedLoss(any(), anyString())).thenReturn(Single.just(TestRules.mock("loss_created.json",
                StockLossVO.class)));
        vm.submitLoss();
        assertEquals("已登记报损 10 千克育肥全价料", TestRules.plain(vm.getLossDone().getValue()));
        verify(repo, times(2)).getStocktakeSheet();
        assertEquals("1856", vm.getActualQty().getValue().get(5102L));
    }
}
