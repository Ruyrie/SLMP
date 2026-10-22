package com.zhimu.slmp.today.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.HomeRepository;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.HomeVO;
import com.zhimu.slmp.today.model.vo.TodoItemVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.LinkNavigator;
import com.zhimu.slmp.today.util.NavTarget;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * TodayViewModel：按角色渲染两版首页、登记回流减指标、长按进 T04、刷新失败保留内容、通知权限只引导一次。
 */
public class TodayViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private HomeRepository homeRepository;
    private TodoRepository todoRepository;
    private TodayServiceImpl todoService;
    private LinkNavigator linkNavigator;
    private AppSettings appSettings;
    private IPushService pushService;
    private TodayViewModel vm;

    @Before
    public void setUp() {
        homeRepository = mock(HomeRepository.class);
        todoRepository = mock(TodoRepository.class);
        todoService = mock(TodayServiceImpl.class);
        linkNavigator = mock(LinkNavigator.class);
        appSettings = mock(AppSettings.class);
        pushService = mock(IPushService.class);
        when(todoService.todoChanges()).thenReturn(Observable.never());
        when(todoService.unreadCount()).thenReturn(new MutableLiveData<>(0));
        vm = new TodayViewModel(homeRepository, todoRepository, todoService, linkNavigator, appSettings, pushService);
    }

    @Test
    public void ownerHome_showsManagerViewAndOwnerGroup() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.OWNER_HOME, HomeVO.class)));

        vm.onFarmSwitched(TestData.farm1001());

        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
        assertEquals("早上好，王建国", vm.getGreetingText().getValue());
        assertEquals("10月19日 周一 · 你是本场所有者", vm.getRoleLine().getValue());
        assertTrue(vm.getManagerView().getValue());
        assertEquals(870, vm.getOwnerMetrics().getValue().getStockCount());
        assertEquals("3 号育肥舍有猪腹泻，先怎么处理？", vm.getAiHint().getValue());
        assertEquals(12, vm.getQuickItems().getValue().size());
        assertFalse(vm.getFarmBlocked().getValue());
        verify(todoService).setUnread(3);
    }

    @Test
    public void staffHome_hidesOwnerGroupAndShowsPens() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.STAFF_HOME, HomeVO.class)));

        vm.onFarmSwitched(TestData.farm1001());

        assertFalse(vm.getManagerView().getValue());
        assertEquals("10月19日 周一 · 东山猪场员工", vm.getRoleLine().getValue());
        assertEquals(7, vm.getStaffMetrics().getValue().getTodayTodoCount());
        assertEquals(2, vm.getMyPens().getValue().size());
        assertEquals(8, vm.getQuickItems().getValue().size());
        for (QuickRegisterItem item : vm.getQuickItems().getValue()) {
            assertFalse(item.isOwnerGroup());
        }
    }

    @Test
    public void pendingFarm_isBlocked() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.PENDING_HOME, HomeVO.class)));

        vm.onFarmSwitched(TestData.farm1001());

        assertTrue(vm.getFarmBlocked().getValue());
        assertFalse(vm.getHasPen().getValue());
    }

    @Test
    public void todoChanged_decrementsMetricsAndFadesItem() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.OWNER_HOME, HomeVO.class)));
        vm.onFarmSwitched(TestData.farm1001());

        vm.onTodoChanged(new TodoChange(90101L, "FEED_TASK", 70101L, 1001));

        assertEquals(15, vm.getOwnerMetrics().getValue().getTodayTodoCount());
        assertEquals(Long.valueOf(90101L), vm.getLeavingTodoId().getValue());

        // 淡出后从列表移除并静默刷新：指标以服务端返回为准（Mock 仍为 16）
        vm.removeTodoLocally(90101L);
        assertTrue(vm.removedIdsSnapshot().contains(90101L));
        verify(homeRepository, org.mockito.Mockito.times(2)).getHome();
        // 同一待办再次回流不重复减
        vm.onTodoChanged(new TodoChange(90101L, null, null, 1001));
        assertEquals(16, vm.getOwnerMetrics().getValue().getTodayTodoCount());
    }

    @Test
    public void longClick_opensTodoDetail() {
        TodoItemVO item = TestData.parse(TestData.todo(90112, "OVERDUE", "WORK"), TodoItemVO.class);

        vm.onTodoLongClick(item);

        NavTarget t = vm.getNavEvent().getValue();
        assertNotNull(t);
        assertEquals("T04", t.getRoute());
        assertEquals("90112", t.param(RouterPath.EXTRA_TODO_ID));
    }

    @Test
    public void metricCards_routeByCard() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.OWNER_HOME, HomeVO.class)));
        vm.onFarmSwitched(TestData.farm1001());

        vm.onMetricClick(TodayViewModel.CARD_TODAY);
        NavTarget t = vm.getNavEvent().getValue();
        assertEquals("T03", t.getRoute());
        assertEquals("DAY", t.param(RouterPath.EXTRA_MODE));
        assertEquals("2026-10-19", t.param(RouterPath.EXTRA_DATE));

        vm.onMetricClick(TodayViewModel.CARD_STOCK);
        assertEquals("L03", vm.getNavEvent().getValue().getRoute());

        vm.onMetricClick(TodayViewModel.CARD_PENS);
        assertEquals(TabRegistry.MY_PENS, vm.getSwitchTab().getValue());
    }

    @Test
    public void refreshFailure_keepsContentAndShowsMessage() {
        when(homeRepository.getHome()).thenReturn(Single.just(TestData.parse(TestData.OWNER_HOME, HomeVO.class)));
        vm.onFarmSwitched(TestData.farm1001());
        when(homeRepository.getHome()).thenReturn(Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")));

        vm.refresh();

        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
        assertEquals("早上好，王建国", vm.getGreetingText().getValue());
        UiMessage msg = vm.getMessage().getValue();
        assertNotNull(msg);
        assertEquals("刷新失败，请检查网络", msg.getText());
    }

    @Test
    public void notifyGuide_onlyOnce() {
        when(appSettings.isNotifyGuideShown()).thenReturn(false);

        vm.checkNotifyGuide(true);
        vm.checkNotifyGuide(true);

        verify(appSettings).setNotifyGuideShown(true);
        vm.onNotifyPermissionResult(true);
        verify(pushService).syncDevice();
    }

    @Test
    public void firstLoadNetworkError_showsErrorState() {
        when(homeRepository.getHome()).thenReturn(Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")));
        when(homeRepository.getCached(anyLong())).thenReturn(null);

        vm.onFarmSwitched(TestData.farm1001());

        assertEquals(UiState.Status.ERROR, vm.getUiState().getValue().getStatus());
    }

    @Test
    public void noFarm_showsEmptyWithoutRequest() {
        vm.onFarmSwitched(null);

        UiState state = vm.getUiState().getValue();
        assertNotNull(state);
        assertEquals(UiState.Status.EMPTY, state.getStatus());
        assertEquals(TodayViewModel.NO_FARM_TITLE, state.getTitle());
    }
}
