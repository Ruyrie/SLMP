package com.zhimu.slmp.today.ui.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.model.vo.MessageDeleteResult;
import com.zhimu.slmp.today.model.vo.ReadAllResult;
import com.zhimu.slmp.today.model.vo.UnreadCountVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.MessageOpenCoordinator;
import com.zhimu.slmp.today.util.MessageOverrides;
import com.zhimu.slmp.today.util.MessageRow;
import com.zhimu.slmp.today.util.NavTarget;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * MessageCenterViewModel 本地覆盖表：删除后加入已删除集合并给出“撤销”，撤销成功移出并刷新、超时限保持删除；
 * 全部已读与没有未读；打开消息先本地标为已读；员工的类型选项。
 */
public class MessageCenterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private MessageRepository repository;
    private MessageOpenCoordinator coordinator;
    private TodayServiceImpl todoService;
    private FarmContext farmContext;
    private MessageCenterViewModel vm;

    @Before
    public void setUp() {
        repository = mock(MessageRepository.class);
        coordinator = mock(MessageOpenCoordinator.class);
        todoService = mock(TodayServiceImpl.class);
        farmContext = mock(FarmContext.class);
        when(repository.getUnreadCount(any(), any())).thenReturn(Single.just(
                TestData.parse("{'total_unread':3,'filtered_unread':3}", UnreadCountVO.class)));
        vm = new MessageCenterViewModel(repository, coordinator, todoService, farmContext);
    }

    private static MessageBriefVO message(long id, boolean read) {
        return TestData.parse(TestData.message(id, read, "AI_DIAGNOSIS"), MessageBriefVO.class);
    }

    @Test
    public void delete_addsToDeletedSetWithUndo() {
        when(repository.delete(501)).thenReturn(Single.just(TestData.parse(
                "{'deleted_at':'2026-10-19 09:31:00','undo_deadline':'2026-10-19 09:32:00'}", MessageDeleteResult.class)));

        vm.deleteMessage(message(501, false));

        assertTrue(vm.currentOverrides().isDeleted(501));
        UiMessage msg = vm.getMessage().getValue();
        assertNotNull(msg);
        assertEquals("已删除这条消息", msg.getText());
        assertEquals("撤销", msg.getActionText());
        assertEquals(MessageCenterViewModel.ACTION_UNDO + 501, msg.getActionId());
    }

    @Test
    public void deleteFailure_keepsMessage() {
        when(repository.delete(501)).thenReturn(Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")));

        vm.deleteMessage(message(501, false));

        assertFalse(vm.currentOverrides().isDeleted(501));
    }

    @Test
    public void undo_restoresAndRefreshes() {
        when(repository.delete(501)).thenReturn(Single.just(TestData.parse("{}", MessageDeleteResult.class)));
        when(repository.restore(501)).thenReturn(Single.just(message(501, true)));
        vm.deleteMessage(message(501, false));

        vm.onMessageAction(MessageCenterViewModel.ACTION_UNDO + 501);

        assertFalse(vm.currentOverrides().isDeleted(501));
        verify(repository).restore(501);
        verify(repository).invalidate();
    }

    @Test
    public void undoAfterDeadline_staysDeleted() {
        when(repository.delete(502)).thenReturn(Single.just(TestData.parse("{}", MessageDeleteResult.class)));
        when(repository.restore(502)).thenReturn(Single.error(new ApiException(ErrorCodes.STATE_CHANGED, "已超过撤销时限")));
        vm.deleteMessage(message(502, false));

        vm.undoDelete(502);

        assertTrue(vm.currentOverrides().isDeleted(502));
        assertEquals("已超过撤销时限", vm.getMessage().getValue().getText());
        verify(repository, never()).invalidate();
    }

    @Test
    public void noticeDeletedInDetail_offersUndo() {
        vm.onNoticeDeleted(507);

        assertTrue(vm.currentOverrides().isDeleted(507));
        assertEquals(MessageCenterViewModel.ACTION_UNDO + 507, vm.getMessage().getValue().getActionId());
    }

    @Test
    public void markAllRead_overridesAndCount() {
        when(repository.readAll(any())).thenReturn(Single.just(
                TestData.parse("{'updated_count':3,'total_unread':0}", ReadAllResult.class)));

        vm.markAllRead();

        assertTrue(vm.currentOverrides().isAllRead());
        assertTrue(vm.currentOverrides().isRead(message(501, false)));
        assertEquals(Integer.valueOf(0), vm.getFilteredUnread().getValue());
        assertEquals("已全部标为已读", vm.getMessage().getValue().getText());
        verify(todoService).setUnread(0);
    }

    @Test
    public void markAllRead_nothingUnread() {
        when(repository.readAll(any())).thenReturn(Single.just(
                TestData.parse("{'updated_count':0,'total_unread':0}", ReadAllResult.class)));

        vm.markAllRead();

        assertFalse(vm.currentOverrides().isAllRead());
        assertEquals("没有未读消息", vm.getMessage().getValue().getText());
    }

    @Test
    public void openMessage_marksReadLocallyAndNavigates() {
        MessageBriefVO m = message(501, false);
        when(coordinator.open(m)).thenReturn(Maybe.just(NavTarget.of("I06")));

        vm.openMessage(m);

        assertTrue(vm.currentOverrides().isRead(m));
        assertEquals("I06", vm.getNavEvent().getValue().getRoute());
    }

    @Test
    public void openMessageNotFound_refreshesAndClearsOverrides() {
        MessageBriefVO m = message(999, false);
        when(coordinator.open(m)).thenReturn(Maybe.error(new ApiException(ErrorCodes.NOT_FOUND, "记录不存在或无权查看")));

        vm.openMessage(m);

        assertEquals("记录不存在或无权查看", vm.getMessage().getValue().getText());
        assertFalse(vm.currentOverrides().isRead(m));
    }

    @Test
    public void overrides_applyInviteStatus() {
        MessageBriefVO invite = TestData.parse(TestData.message(504, false, "FARM_INVITE"), MessageBriefVO.class);
        MessageOverrides o = MessageOverrides.empty();
        MessageRow before = o.apply(invite);
        assertFalse(before.isRead());
        assertTrue(before.isActionPending());

        MessageRow after = o.setActionStatus(504, MessageRow.STATUS_ACCEPTED).apply(invite);
        assertTrue(after.isRead());
        assertFalse(after.isActionPending());
        assertEquals(MessageRow.STATUS_ACCEPTED, after.getActionStatus());
    }

    @Test
    public void filters_categoryAndClear() {
        vm.setCategory("AI");
        assertEquals("AI", vm.currentFilter().category);
        assertTrue(vm.hasFilter());

        vm.toggleUnread();
        assertTrue(vm.currentFilter().unreadOnly);

        vm.clearFilters();
        assertNull(vm.currentFilter().category);
        assertFalse(vm.currentFilter().unreadOnly);
        assertFalse(vm.hasFilter());
    }

    @Test
    public void staff_hasFourCategoriesAndNoFarmFilter() {
        when(farmContext.isStaff()).thenReturn(true);
        MessageCenterViewModel staffVm = new MessageCenterViewModel(repository, coordinator, todoService, farmContext);

        assertEquals(5, staffVm.getCategoryOptions().getValue().size());
        assertFalse(staffVm.getShowFarmFilter().getValue());
        assertEquals("全部类型", MessageCenterViewModel.categoryName(MessageCenterViewModel.CATEGORY_ALL));
        assertEquals("成员与养殖场", MessageCenterViewModel.categoryName("MEMBER"));
        verify(repository, never()).delete(anyLong());
    }
}
