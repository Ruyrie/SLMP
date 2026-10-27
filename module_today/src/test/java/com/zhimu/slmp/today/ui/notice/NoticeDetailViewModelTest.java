package com.zhimu.slmp.today.ui.notice;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.TestData;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.vo.MessageDetailVO;
import com.zhimu.slmp.today.model.vo.NoticePayload;
import com.zhimu.slmp.today.service.TodayServiceImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NoticeDetailViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public RxTestRule rx = new RxTestRule();

    private MessageRepository repository;
    private NoticeDetailViewModel vm;
    private TestScheduler clock;

    @Before public void setUp() {
        clock = new TestScheduler();
        RxJavaPlugins.setComputationSchedulerHandler(s -> clock);
        repository = mock(MessageRepository.class);
        vm = new NoticeDetailViewModel(repository, mock(TodayServiceImpl.class));
    }

    private static MessageDetailVO notice(boolean revoked) {
        MessageDetailVO detail = TestData.parse("{'message_id':501,'msg_type':'NOTICE','revoked':" + revoked + "}",
                MessageDetailVO.class);
        detail.setNotice(TestData.parse(revoked
                ? "{'revoked':true,'sent_at':'2026-10-25 09:00:00','revoked_at':'2026-10-25 10:00:00'}"
                : "{'title':'平台通知','content':'请注意防寒','image':{'file_id':51,'url':'/notice.jpg'}}",
                NoticePayload.class));
        return detail;
    }

    @Test public void revokedWhileOpen_hidesContentAndStopsPolling() {
        when(repository.getDetail(501)).thenReturn(Single.just(notice(false)), Single.just(notice(true)));
        vm.load(501);
        vm.onVisible();
        assertNotNull(vm.getNotice().getValue().getImage());
        clock.advanceTimeBy(60, TimeUnit.SECONDS);
        assertNotNull(vm.getRevokedLine().getValue());
        assertNull(vm.getNotice().getValue().getImage());
        assertNull(vm.getNotice().getValue().getContent());
        clock.advanceTimeBy(5, TimeUnit.MINUTES);
        verify(repository, times(2)).getDetail(501);
    }

    @Test public void backgroundStopsPolling_returnRefreshesImmediately() {
        when(repository.getDetail(501)).thenReturn(Single.just(notice(false)), Single.just(notice(true)));
        vm.load(501);
        vm.onVisible();
        vm.onInvisible();
        clock.advanceTimeBy(2, TimeUnit.MINUTES);
        verify(repository).getDetail(501);
        vm.onVisible();
        assertNotNull(vm.getRevokedLine().getValue());
        verify(repository, times(2)).getDetail(501);
    }

    @Test public void temporaryRefreshFailure_keepsContentAndRetries() {
        when(repository.getDetail(501)).thenReturn(Single.just(notice(false)),
                Single.error(new ApiException(ErrorCodes.NETWORK, "网络不可用")), Single.just(notice(true)));
        vm.load(501);
        vm.onVisible();
        vm.onVisible();
        clock.advanceTimeBy(60, TimeUnit.SECONDS);
        assertEquals("请注意防寒", vm.getNotice().getValue().getContent());
        assertTrue(vm.getUiState().getValue().is(UiState.Status.CONTENT));
        assertNull(vm.getMessage().getValue());
        clock.advanceTimeBy(60, TimeUnit.SECONDS);
        assertNotNull(vm.getRevokedLine().getValue());
        verify(repository, times(3)).getDetail(501);
    }

    @Test public void slowRefresh_isNotDuplicatedAndIsCancelledWhenLeaving() {
        SingleSubject<MessageDetailVO> slow = SingleSubject.create();
        when(repository.getDetail(501)).thenReturn(Single.just(notice(false)), slow, Single.just(notice(true)));
        vm.load(501);
        vm.onVisible();
        clock.advanceTimeBy(3, TimeUnit.MINUTES);
        verify(repository, times(2)).getDetail(501);
        assertTrue(slow.hasObservers());
        vm.onInvisible();
        assertFalse(slow.hasObservers());
        vm.onVisible();
        assertNotNull(vm.getRevokedLine().getValue());
        verify(repository, times(3)).getDetail(501);
    }
}
