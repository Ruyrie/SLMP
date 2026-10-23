package com.zhimu.slmp.stock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagingSource;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.network.model.PageVO;
import com.zhimu.slmp.stock.TestRules;
import com.zhimu.slmp.stock.data.StockRepository;
import com.zhimu.slmp.stock.model.vo.DrugArchiveItemVO;
import com.zhimu.slmp.stock.model.vo.DrugArchiveListResult;
import com.zhimu.slmp.stock.model.vo.DrugLotItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockItemVO;
import com.zhimu.slmp.stock.model.vo.FeedStockOverviewVO;
import com.zhimu.slmp.stock.model.vo.StockActionsVO;
import com.zhimu.slmp.stock.ui.common.ListRow;
import com.zhimu.slmp.stock.ui.common.RecordMenu;
import com.zhimu.slmp.stock.ui.drugstock.DrugStockViewModel;
import com.zhimu.slmp.stock.ui.drugstock.LotPagingSource;
import com.zhimu.slmp.stock.ui.feedstock.FeedStockViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * M01 概览加载与横幅、空状态；M05 批号分页先“需要处理”后“正常”并插入分组标题、搜索不分组、药品档案面板副文字；
 * 只读菜单按 actions（当天修改 / 作废、次日起提交修改申请、待审核只留查看修改申请）。
 */
public class ListPagesViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public TestRules rules = new TestRules();

    private StockRepository repo;
    private FarmContext farm;

    @Before
    public void setUp() {
        repo = mock(StockRepository.class);
        farm = mock(FarmContext.class);
        when(farm.getContext()).thenReturn(new MutableLiveData<FarmContextVO>());
        when(farm.currentFarmId()).thenReturn(1001L);
    }

    @Test
    public void feedStock_loadsBannerAndFlows() throws IOException {
        Type pageType = new TypeToken<PageVO<FeedStockItemVO>>() {
        }.getType();
        when(repo.getFeedStockOverview()).thenReturn(Single.just(TestRules.mock("feed_overview.json",
                FeedStockOverviewVO.class)));
        when(repo.pageFeeds(anyInt(), anyInt())).thenReturn(Single.just(TestRules.mock("feeds_page.json", pageType)));
        FeedStockViewModel vm = new FeedStockViewModel(repo, farm);
        vm.getCurrentFarmId().observeForever(v -> {
        });
        vm.attachSavedState(new SavedStateHandle());
        assertTrue(vm.getUiState().getValue().is(UiState.Status.CONTENT));
        assertEquals("育肥全价料库存不足，剩余可用 1 天，低于预警天数 7 天。", vm.getBannerText().getValue());
        assertEquals(2, vm.getFeeds().getValue().size());
        assertEquals(5, vm.getRecentFlows().getValue().size());
        assertTrue(vm.getShowOtherOut().getValue());
        assertFalse(vm.getReadonly().getValue());
        vm.onBannerAction();
        assertEquals(Long.valueOf(5101), vm.getOpenInbound().getValue());
    }

    @Test
    public void feedStock_noFeedIsEmpty() throws IOException {
        FeedStockOverviewVO o = TestRules.mock("feed_overview.json", FeedStockOverviewVO.class);
        o.hasFeed = false;
        o.farmReadonly = true;
        when(repo.getFeedStockOverview()).thenReturn(Single.just(o));
        when(repo.pageFeeds(anyInt(), anyInt())).thenReturn(Single.just(new PageVO<>()));
        FeedStockViewModel vm = new FeedStockViewModel(repo, farm);
        vm.attachSavedState(new SavedStateHandle());
        assertTrue(vm.getUiState().getValue().is(UiState.Status.EMPTY));
        assertFalse(vm.getShowOtherOut().getValue());
        assertEquals("养殖场已停用，库存数据只能查看。", vm.getReadonlyText().getValue());
    }

    @Test
    public void lotPaging_attentionThenNormalWithHeaders() throws Exception {
        Type t = new TypeToken<PageVO<DrugLotItemVO>>() {
        }.getType();
        PageVO<DrugLotItemVO> att = TestRules.mock("lots_attention.json", t);
        PageVO<DrugLotItemVO> nor = TestRules.mock("lots_normal.json", t);
        when(repo.pageDrugLots(eq("ATTENTION"), isNull(), isNull(), anyInt(), anyInt())).thenReturn(Single.just(att));
        when(repo.pageDrugLots(eq("NORMAL"), isNull(), isNull(), anyInt(), anyInt())).thenReturn(Single.just(nor));
        LotPagingSource src = new LotPagingSource(repo, null, null, null);
        PagingSource.LoadResult.Page<Integer, ListRow<DrugLotItemVO>> p1 = page(src, null);
        assertEquals("需要处理 · 3 个批号", p1.getData().get(0).header);
        assertEquals(4, p1.getData().size());
        PagingSource.LoadResult.Page<Integer, ListRow<DrugLotItemVO>> p2 = page(src, p1.getNextKey());
        assertEquals("正常 · 4 个批号", p2.getData().get(0).header);
        assertEquals(5, p2.getData().size());
        assertNull(p2.getNextKey());

        when(repo.pageDrugLots(eq("ALL"), isNull(), eq("口蹄疫"), anyInt(), anyInt()))
                .thenReturn(Single.just(TestRules.mock("lots_search_fmd.json", t)));
        LotPagingSource search = new LotPagingSource(repo, null, "口蹄疫", null);
        PagingSource.LoadResult.Page<Integer, ListRow<DrugLotItemVO>> s = page(search, null);
        assertEquals(1, s.getData().size());
        assertFalse(s.getData().get(0).isHeader());
    }

    @SuppressWarnings("unchecked")
    private static PagingSource.LoadResult.Page<Integer, ListRow<DrugLotItemVO>> page(LotPagingSource src,
                                                                                       Integer key) {
        PagingSource.LoadParams<Integer> params = key == null
                ? new PagingSource.LoadParams.Refresh<>(null, 20, false)
                : new PagingSource.LoadParams.Append<>(key, 20, false);
        return (PagingSource.LoadResult.Page<Integer, ListRow<DrugLotItemVO>>) src.loadSingle(params).blockingGet();
    }

    @Test
    public void drugStock_archivePickerTexts() throws IOException {
        DrugArchiveListResult r = TestRules.mock("drugs_archive.json", DrugArchiveListResult.class);
        when(repo.listDrugs(isNull(), isNull())).thenReturn(Single.just(r));
        DrugStockViewModel vm = new DrugStockViewModel(repo, farm);
        vm.openArchivePicker();
        assertEquals("共 5 种 · 点按查看或修改", vm.archiveSubtitleText());
        List<com.zhimu.slmp.stock.ui.common.PickerItem> items = vm.getArchivePicker().getValue().items;
        assertEquals("兽药", items.get(0).title);
        assertEquals("可用结存 12 袋 · 下限 15 袋", TestRules.plain(items.get(1).subtitle));
        assertEquals("库存不足", items.get(1).tag);
        DrugArchiveItemVO fmd = r.list.get(3);
        boolean found = false;
        for (com.zhimu.slmp.stock.ui.common.PickerItem i : items) {
            if (i.value.equals(String.valueOf(fmd.drugId))) {
                assertEquals("暂无库存", i.subtitle);
                found = true;
            }
        }
        assertTrue(found);
        vm.setCategory("VACCINE");
        assertEquals("没有疫苗库存。登记入库后会显示在这里。", vm.emptyText());
        vm.search("口蹄疫");
        assertEquals("没有找到“口蹄疫”相关的批号", vm.emptyText());
    }

    @Test
    public void recordMenu_followsActions() {
        StockActionsVO editable = new StockActionsVO();
        editable.editState = "EDITABLE";
        editable.canEdit = true;
        editable.canVoid = true;
        assertEquals(Arrays.asList(RecordMenu.EDIT, RecordMenu.VOID, RecordMenu.CHANGE_LOG),
                RecordMenu.items(editable, true));
        assertEquals(Arrays.asList(RecordMenu.REQUEST, RecordMenu.CHANGE_LOG), RecordMenu.items(editable, false));
        StockActionsVO request = new StockActionsVO();
        request.editState = "REQUEST_ONLY";
        request.canRequestChange = true;
        assertEquals(Arrays.asList(RecordMenu.REQUEST, RecordMenu.CHANGE_LOG), RecordMenu.items(request, true));
        StockActionsVO pending = new StockActionsVO();
        pending.editState = "PENDING";
        pending.pendingRequestId = 8801L;
        assertEquals(Arrays.asList(RecordMenu.VIEW_REQUEST), RecordMenu.items(pending, true));
    }
}
