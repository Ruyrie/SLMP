package com.zhimu.slmp.today.ui.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.TodoRepository;
import com.zhimu.slmp.today.model.vo.TodoDetailVO;
import com.zhimu.slmp.today.service.TodayServiceImpl;
import com.zhimu.slmp.today.util.LinkNavigator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * TodoDetailViewModel：忽略原因 0 / 1 / 100 / 101 字；网络失败沿用幂等键；超时先查后判；冲突后幂等键置空。
 */
public class TodoDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private TodoRepository repository;
    private TodoDetailViewModel vm;

    private static String repeat(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append('字');
        }
        return sb.toString();
    }

    private static TodoDetailVO detail(String status) {
        return TestData.parse("{'todo_id':90112,'farm_id':1001,'todo_type':'DISINFECTION','category':'WORK',"
                + "'display_status':'" + status + "','title':'2 号保育舍 · 消毒','version':3,'can_register':true,"
                + "'can_complete':true,'can_ignore':true,'complete_way':'MANUAL','mark_done_tip':'手动标记完成不会自动生成消毒记录'}",
                TodoDetailVO.class);
    }

    @Before
    public void setUp() {
        repository = mock(TodoRepository.class);
        TodayServiceImpl todoService = mock(TodayServiceImpl.class);
        when(todoService.todoChanges()).thenReturn(Observable.never());
        vm = new TodoDetailViewModel(repository, todoService, mock(LinkNavigator.class));
        when(repository.getDetail(90112)).thenReturn(Single.just(detail("OVERDUE")));
        vm.load(90112);
    }

    @Test
    public void reasonLength() {
        assertEquals("请填写忽略原因", TodoDetailViewModel.validateReason(""));
        assertEquals("请填写忽略原因", TodoDetailViewModel.validateReason("   "));
        assertNull(TodoDetailViewModel.validateReason(repeat(1)));
        assertNull(TodoDetailViewModel.validateReason(repeat(100)));
        assertEquals("忽略原因不能超过 100 字", TodoDetailViewModel.validateReason(repeat(101)));
    }

    @Test
    public void workTodo_ignoreInMenu() {
        assertEquals(Boolean.TRUE, vm.getCanComplete().getValue());
        assertEquals(Boolean.TRUE, vm.getIgnoreInMenu().getValue());
        // 值为空的信息行不生成
        assertTrue(TodoDetailViewModel.buildRows(detail("OVERDUE")).isEmpty());
    }

    @Test
    public void networkFailure_reusesIdempotencyKey() {
        when(repository.complete(eq(90112L), anyString(), any()))
                .thenReturn(Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")));
        // 超时先查后判：仍为已逾期，按失败处理并保留幂等键
        vm.confirmComplete();
        String first = vm.getIdemKey();
        assertNotNull(first);
        assertEquals("网络连接不稳定，没有提交成功。请稍后重试。", vm.getMessage().getValue().getText());

        vm.confirmComplete();
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).complete(eq(90112L), keys.capture(), any());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));

        vm.onDialogDismissed();
        assertNull(vm.getIdemKey());
    }

    @Test
    public void timeoutButAlreadyDone_treatedAsSuccess() {
        when(repository.complete(eq(90112L), anyString(), any()))
                .thenReturn(Single.error(new ApiException(ErrorCodes.NETWORK, "超时")));
        when(repository.getDetail(90112)).thenReturn(Single.just(detail("DONE")));

        vm.confirmComplete();

        assertEquals("DONE", vm.getDetail().getValue().getDisplayStatus());
        assertNull(vm.getIdemKey());
        assertEquals("已标记完成", vm.getMessage().getValue().getText());
    }

    @Test
    public void conflict_clearsKeyAndReloads() {
        when(repository.ignore(eq(90112L), anyString(), any()))
                .thenReturn(Single.error(new ApiException(ErrorCodes.STATE_CHANGED, "内容已更新，请刷新后再修改")));

        vm.confirmIgnore("这批猪计划 11月中旬再出栏");

        assertNull(vm.getIdemKey());
        assertEquals("内容已更新，请刷新后再修改", vm.getMessage().getValue().getText());
        verify(repository, times(2)).getDetail(anyLong());
    }
}
