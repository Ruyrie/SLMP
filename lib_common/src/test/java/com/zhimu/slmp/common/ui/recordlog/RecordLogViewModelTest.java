package com.zhimu.slmp.common.ui.recordlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.data.BaseDataRepository;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.network.GsonFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * U14 修改记录面板的 ViewModel：加载、空、失败与重新加载、无权查看、记录未保存；以及每条记录的显示文字。
 */
public class RecordLogViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    /** 与 Mock 数据同格式：一条普通修改（两个字段，来自修改申请）、一条改期（带原因）、一条作废（没有字段变化） */
    private static final String LOGS = "["
            + "{'id':1,'operator':{'user_id':10002,'name':'刘芳'},'op_action':'UPDATE','op_action_text':'修改',"
            + "'changes':[{'field_name':'容量','before':'200 头','after':'220 头'},"
            + "{'field_name':'备注','before':null,'after':'靠东侧'}],"
            + "'change_request_no':'CR2610190008','created_at':'2026-10-12 14:20:00'},"
            + "{'id':2,'operator':{'user_id':10001,'name':'王建国'},'op_action':'POSTPONE','op_action_text':'调整日期',"
            + "'changes':[{'field_name':'猪瘟疫苗 首免','before':'10月26日','after':'11月2日'}],"
            + "'reason':'部分猪只发热，暂缓接种','created_at':'2026-10-19 09:30:00'},"
            + "{'id':3,'operator':null,'op_action':'VOID','op_action_text':'作废','changes':[],"
            + "'created_at':'2026-10-10 08:00:00'}]";

    private BaseDataRepository repository;

    @Before
    public void setUp() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        DateFormatter.setClock(Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), DateFormatter.ZONE));
        repository = mock(BaseDataRepository.class);
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
        DateFormatter.setClock(Clock.system(DateFormatter.ZONE));
    }

    @Test
    public void load_showsLogs() {
        when(repository.getRecordLogs("PEN", 2003L)).thenReturn(Single.just(logs()));
        RecordLogViewModel vm = create("PEN", 2003L);

        verify(repository).getRecordLogs("PEN", 2003L);
        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
        assertEquals(3, vm.getLogs().getValue().size());
        assertEquals(1L, vm.getLogs().getValue().get(0).getId());
    }

    @Test
    public void load_emptyList_showsEmpty() {
        when(repository.getRecordLogs("FEED_PLAN", 4001L)).thenReturn(Single.just(Collections.emptyList()));
        RecordLogViewModel vm = create("FEED_PLAN", 4001L);

        assertEquals(UiState.Status.EMPTY, vm.getUiState().getValue().getStatus());
        assertTrue(vm.getLogs().getValue().isEmpty());
    }

    @Test
    public void load_failure_showsErrorAndRetryReloads() {
        when(repository.getRecordLogs("INSPECTION", 5001L))
                .thenReturn(Single.error(ApiException.network(null)))
                .thenReturn(Single.just(logs()));
        RecordLogViewModel vm = create("INSPECTION", 5001L);

        UiState state = vm.getUiState().getValue();
        assertEquals(UiState.Status.ERROR, state.getStatus());
        assertEquals(ErrorCodes.NETWORK, state.getErrorCode());
        assertTrue(vm.getLogs().getValue().isEmpty());

        vm.retry();
        verify(repository, times(2)).getRecordLogs("INSPECTION", 5001L);
        assertEquals(UiState.Status.CONTENT, vm.getUiState().getValue().getStatus());
        assertEquals(3, vm.getLogs().getValue().size());
    }

    @Test
    public void load_noPermission_showsNoPermission() {
        when(repository.getRecordLogs("SALE_ORDER", 8001L))
                .thenReturn(Single.error(new ApiException(ErrorCodes.ROLE_FORBIDDEN, "只有所有者和场长可以查看")));
        RecordLogViewModel vm = create("SALE_ORDER", 8001L);

        assertEquals(UiState.Status.NO_PERMISSION, vm.getUiState().getValue().getStatus());
    }

    @Test
    public void unsavedRecord_showsEmptyWithoutRequest() {
        RecordLogViewModel vm = create("FEED_PLAN", 0L);

        verify(repository, never()).getRecordLogs(anyString(), anyLong());
        assertEquals(UiState.Status.EMPTY, vm.getUiState().getValue().getStatus());
    }

    @Test
    public void texts_titleAndContent() {
        List<RecordLogVO> list = logs();

        assertEquals("10月12日 14:20 · 刘芳", RecordLogTexts.title(list.get(0)));
        assertEquals("容量：200 头 → 220 头\n备注：无 → 靠东侧\n修改申请 CR2610190008",
                RecordLogTexts.content(list.get(0)));

        assertEquals("今天 09:30 · 王建国", RecordLogTexts.title(list.get(1)));
        assertEquals("调整日期\n猪瘟疫苗 首免：10月26日 → 11月2日\n原因：部分猪只发热，暂缓接种",
                RecordLogTexts.content(list.get(1)));

        assertEquals("10月10日 08:00", RecordLogTexts.title(list.get(2)));
        assertEquals("作废", RecordLogTexts.content(list.get(2)));
    }

    private RecordLogViewModel create(String bizType, long bizId) {
        RecordLogViewModel vm = new RecordLogViewModel(repository);
        SavedStateHandle handle = new SavedStateHandle();
        handle.set(RouterPath.EXTRA_BIZ_TYPE, bizType);
        handle.set(RouterPath.EXTRA_BIZ_ID, bizId);
        vm.attachSavedState(handle);
        return vm;
    }

    private static List<RecordLogVO> logs() {
        return GsonFactory.get().fromJson(LOGS.replace('\'', '"'), new TypeToken<List<RecordLogVO>>() {
        }.getType());
    }
}
