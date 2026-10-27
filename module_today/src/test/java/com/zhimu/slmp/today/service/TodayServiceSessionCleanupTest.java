package com.zhimu.slmp.today.service;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.gson.Gson;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.data.MessageRepository;
import com.zhimu.slmp.today.model.vo.UnreadCountVO;
import org.junit.Rule;
import org.junit.Test;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TodayServiceSessionCleanupTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule public RxTestRule rx = new RxTestRule();

    @Test public void cancellationStopsOldRequestAndAllowsNewAccountUnreadRefresh() {
        MessageRepository repository = mock(MessageRepository.class);
        SingleSubject<UnreadCountVO> old = SingleSubject.create();
        when(repository.getUnreadCount(null, null)).thenReturn(old);
        TodayServiceImpl service = new TodayServiceImpl(repository);
        service.setUnread(12);
        service.refreshUnread();
        service.clearSessionState();
        assertEquals(Integer.valueOf(0), service.unreadCount().getValue());
        assertFalse(old.hasObservers());
        old.onSuccess(count(19));
        assertEquals(Integer.valueOf(0), service.unreadCount().getValue());
        when(repository.getUnreadCount(null, null)).thenReturn(Single.just(count(3)));
        service.refreshUnread();
        assertEquals(Integer.valueOf(3), service.unreadCount().getValue());
    }

    private static UnreadCountVO count(int value) {
        return new Gson().fromJson("{\"totalUnread\":" + value + "}", UnreadCountVO.class);
    }
}
