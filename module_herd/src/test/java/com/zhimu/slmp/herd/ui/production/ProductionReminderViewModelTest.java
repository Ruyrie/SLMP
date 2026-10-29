package com.zhimu.slmp.herd.ui.production;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.TestRules;
import com.zhimu.slmp.herd.data.StockRepository;
import com.zhimu.slmp.herd.model.vo.ProductionOverviewVO;
import com.zhimu.slmp.herd.model.vo.ProductionReminderItemVO;
import com.zhimu.slmp.herd.ui.common.NavTarget;
import io.reactivex.rxjava3.core.Single;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 生产提醒页：列出 API-L01 的全部提醒（顺序不变），0 条显示空状态；点击行为与原生产页列表相同。 */
public class ProductionReminderViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public TestRules schedulers = new TestRules();

    private static ProductionReminderViewModel vmWith(String overview) {
        StockRepository repository = mock(StockRepository.class);
        if (overview != null) {
            when(repository.getOverview()).thenReturn(Single.just(TestRules.parse(overview, ProductionOverviewVO.class)));
        }
        return new ProductionReminderViewModel(repository);
    }

    private static ProductionReminderItemVO item(String json) {
        return TestRules.parse(json, ProductionReminderItemVO.class);
    }

    @Test public void listsAllRemindersInServerOrder() {
        ProductionReminderViewModel vm = vmWith("{'farm_status':'NORMAL','reminder_total':3,'reminders':["
                + "{'reminder_type':'WITHDRAWAL','title':'ZM2610-031 休药中','batch_id':3001,'route':'D05'},"
                + "{'reminder_type':'URGENT_DIAGNOSIS','title':'3 号育肥舍 · AI 诊断待处理','route':'I06'},"
                + "{'reminder_type':'ABNORMAL_REPORT','title':'3 号育肥舍 · 异常上报待诊断','report_id':6001}]}");
        vm.load();
        assertEquals(3, vm.getReminders().getValue().size());
        assertEquals("WITHDRAWAL", vm.getReminders().getValue().get(0).getReminderType());
        assertEquals("ABNORMAL_REPORT", vm.getReminders().getValue().get(2).getReminderType());
        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
    }

    @Test public void noRemindersShowsEmptyState() {
        ProductionReminderViewModel vm = vmWith("{'farm_status':'NORMAL','reminder_total':0,'reminders':[]}");
        vm.load();
        assertTrue(vm.getReminders().getValue().isEmpty());
        assertEquals(UiState.Status.EMPTY, vm.getUiState().getValue().getStatus());
    }

    @Test public void pendingAuditFarmHasNoReminders() {
        ProductionReminderViewModel vm = vmWith("{'farm_status':'PENDING','reminders':[{'reminder_type':'WITHDRAWAL'}]}");
        vm.load();
        assertTrue(vm.getReminders().getValue().isEmpty());
        assertEquals(UiState.Status.EMPTY, vm.getUiState().getValue().getStatus());
    }

    @Test public void abnormalReportReminderOpensDiagnosisDetailWithReportId() {
        ProductionReminderViewModel vm = vmWith(null);
        vm.onReminderClick(item("{'reminder_type':'ABNORMAL_REPORT','is_warning':true,"
                + "'tag_text':'待处理','batch_id':3001,'todo_id':7201,'report_id':6001,'route':'I06'}"));
        NavTarget target = vm.getNavEvent().getValue();
        assertEquals(RouterPath.I06, target.getAction());
        assertEquals(6001L, target.get(RouterPath.EXTRA_REPORT_ID));
    }

    @Test public void abnormalReportWithoutReportIdFallsBackToTodoDetail() {
        ProductionReminderViewModel vm = vmWith(null);
        vm.onReminderClick(item("{'reminder_type':'ABNORMAL_REPORT','todo_id':7201,'route':'I06'}"));
        NavTarget target = vm.getNavEvent().getValue();
        assertEquals(RouterPath.T04, target.getAction());
        assertEquals(7201L, target.get(RouterPath.EXTRA_TODO_ID));
        assertNull(target.get(RouterPath.EXTRA_REPORT_ID));
    }

    @Test public void withdrawalReminderOpensDrugPageWithBatch() {
        ProductionReminderViewModel vm = vmWith(null);
        vm.onReminderClick(item("{'reminder_type':'WITHDRAWAL','batch_id':3001,'route':'D05'}"));
        NavTarget target = vm.getNavEvent().getValue();
        assertEquals(RouterPath.D05, target.getAction());
        assertEquals(3001L, target.get(RouterPath.EXTRA_BATCH_ID));
    }

    @Test public void unknownRouteDoesNothing() {
        ProductionReminderViewModel vm = vmWith(null);
        vm.onReminderClick(item("{'reminder_type':'OTHER','route':'ZZ99'}"));
        assertNull(vm.getNavEvent().getValue());
    }
}
