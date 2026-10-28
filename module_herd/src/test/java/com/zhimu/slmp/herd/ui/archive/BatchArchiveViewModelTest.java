package com.zhimu.slmp.herd.ui.archive;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.model.vo.TimelineItemVO;
import com.zhimu.slmp.herd.ui.common.MenuItemSpec;
import com.zhimu.slmp.herd.ui.common.NavTarget;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class BatchArchiveViewModelTest {
    @Rule public InstantTaskExecutorRule main = new InstantTaskExecutorRule();
    @Rule public TestRules rules = new TestRules();
    private BatchArchiveViewModel vm;

    @Before public void setUp() {
        vm = new BatchArchiveViewModel(mock(BatchRepository.class), mock(FarmContext.class));
    }

    private TimelineItemVO item(String type, String action) {
        return TestRules.parse("{'id':900,'biz_id':41,'record_type':'" + type + "','action':'" + action + "'}", TimelineItemVO.class);
    }

    private void detail(boolean direct) {
        BatchDetailVO row = TestRules.parse("{'batch_id':12,'entry_event_id':41,'actions':{" +
                "'can_edit_entry':" + direct + ",'can_submit_change_request':true}}", BatchDetailVO.class);
        ((MutableLiveData<BatchDetailVO>) vm.getDetail()).setValue(row);
    }

    @Test public void feedRecordFromStockFlowIsFoundAndExpandedOnce() {
        vm.focusOn(41L);
        TimelineItemVO weigh = TestRules.parse("{'id':901,'biz_id':41,'record_type':'WEIGH'}", TimelineItemVO.class);
        TimelineItemVO feed = TestRules.parse("{'id':902,'biz_id':41,'record_type':'FEED','expandable':true}",
                TimelineItemVO.class);
        assertFalse(vm.isFocusRecord(weigh));
        assertTrue(vm.isFocusRecord(feed));

        vm.onFocusFound(feed);

        assertNull(vm.getFocusRecordId());
        assertTrue(vm.getExpandedIds().getValue().contains(902L));
        assertFalse(vm.isFocusRecord(feed));
    }

    @Test public void choosingAnotherFilterStopsLocating() {
        vm.focusOn(41L);
        vm.clearFocus();
        assertNull(vm.getFocusRecordId());
    }

    @Test public void freshEntryOffersDirectEditInsteadOfAnUnsupportedApprovalForm() {
        detail(true);
        vm.onMoreClick();
        List<MenuItemSpec> menu = vm.getMenuEvent().getValue();
        assertNotNull(menu);
        assertEquals(1, menu.size());
        assertEquals(BatchArchiveViewModel.MENU_EDIT_ENTRY, menu.get(0).id);
        assertFalse(menu.get(0).review);
        vm.onMenuSelected(BatchArchiveViewModel.MENU_EDIT_ENTRY);
        NavTarget nav = vm.getNavEvent().getValue();
        assertNotNull(nav);
        assertEquals(RouterPath.L08, nav.getAction());
        assertEquals(41L, nav.get(RouterPath.EXTRA_ENTRY_EVENT_ID));
    }

    @Test public void lockedEntryStillUsesTheEntryRecordForApproval() {
        detail(false);
        vm.onMoreClick();
        assertEquals(BatchArchiveViewModel.MENU_CHANGE_REQUEST, vm.getMenuEvent().getValue().get(0).id);
        vm.onMenuSelected(BatchArchiveViewModel.MENU_CHANGE_REQUEST);
        NavTarget nav = vm.getNavEvent().getValue();
        assertEquals(RouterPath.U01, nav.getAction());
        assertEquals(HerdExtras.BIZ_BATCH_ENTRY, nav.get(RouterPath.EXTRA_BIZ_TYPE));
        assertEquals(41L, nav.get(RouterPath.EXTRA_BIZ_ID));
    }

    @Test public void approvalRoutingKeepsEachRecordBusinessTypeAndId() {
        String[][] cases = {{"ENTRY", "BATCH_ENTRY"}, {"TRANSFER", "BATCH_TRANSFER"},
                {"DEATH", "BATCH_DEATH"}};
        for (String[] pair : cases) {
            vm.onRecordMenuSelected(item(pair[0], "CHANGE_REQUEST"), BatchArchiveViewModel.MENU_RECORD_REQUEST);
            NavTarget nav = vm.getNavEvent().getValue();
            assertEquals(RouterPath.U01, nav.getAction());
            assertEquals(pair[1], nav.get(RouterPath.EXTRA_BIZ_TYPE));
            assertEquals(41L, nav.get(RouterPath.EXTRA_BIZ_ID));
        }
    }

    @Test public void directEntryUsesItsEventIdAndNeverOpensApproval() {
        vm.onRecordMenuSelected(item("ENTRY", "EDIT"), BatchArchiveViewModel.MENU_RECORD_EDIT);
        NavTarget nav = vm.getNavEvent().getValue();
        assertEquals(RouterPath.L08, nav.getAction());
        assertEquals(41L, nav.get(RouterPath.EXTRA_ENTRY_EVENT_ID));
    }

    @Test public void saleRoutesToItsDetailToUsePaymentSpecificEditPermissions() {
        vm.onRecordLongClick(item("SALE", "CHANGE_REQUEST"));
        assertEquals(BatchArchiveViewModel.MENU_RECORD_VIEW_SALE,
                vm.getRecordMenuEvent().getValue().items.get(0).id);
        vm.onRecordMenuSelected(item("SALE", "CHANGE_REQUEST"), BatchArchiveViewModel.MENU_RECORD_VIEW_SALE);
        NavTarget nav = vm.getNavEvent().getValue();
        assertEquals(RouterPath.E07, nav.getAction());
        assertEquals(41L, nav.get(RouterPath.EXTRA_SALE_ORDER_ID));
    }

    @Test public void unsupportedOrNonEditableRecordsDoNotFallBackToDeath() {
        vm.onRecordMenuSelected(item("UNKNOWN", "CHANGE_REQUEST"), BatchArchiveViewModel.MENU_RECORD_REQUEST);
        vm.onRecordMenuSelected(item("ENTRY", "NONE"), BatchArchiveViewModel.MENU_RECORD_EDIT);
        vm.onRecordMenuSelected(item("ENTRY", "EDIT"), BatchArchiveViewModel.MENU_RECORD_REQUEST);
        assertNull(vm.getNavEvent().getValue());
    }
}
