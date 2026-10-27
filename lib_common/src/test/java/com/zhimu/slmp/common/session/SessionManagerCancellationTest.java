package com.zhimu.slmp.common.session;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.google.gson.Gson;
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ServiceHub;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SessionManagerCancellationTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    private SessionStore store;
    private FarmContext farm;
    private AppDatabase database;
    private AccountDataCleaner cleaner;
    private IPushService push, previousPush;
    private SessionManager session;

    @Before public void setUp() {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        store = mock(SessionStore.class);
        when(store.getAccessToken()).thenReturn("old-access");
        when(store.getRefreshToken()).thenReturn("old-refresh");
        when(store.getUserJson()).thenReturn(new Gson().toJson(new AccountUserVO(7, null, null, null)));
        farm = mock(FarmContext.class);
        database = mock(AppDatabase.class);
        cleaner = mock(AccountDataCleaner.class);
        push = mock(IPushService.class);
        previousPush = ServiceHub.get(IPushService.class);
        ServiceHub.register(IPushService.class, push);
        session = new SessionManager(store, farm, database, new Gson(), () -> mock(SessionApi.class), cleaner);
    }

    @After public void tearDown() {
        RxJavaPlugins.reset();
        ServiceHub.register(IPushService.class, previousPush == null ? mock(IPushService.class) : previousPush);
    }

    @Test public void cancellation_clearsPushAndWaitsForOwnedDataCleanup() {
        assertTrue(session.clearAfterAccountCancellation(7));
        assertNull(session.currentUser());
        assertNull(session.getAccessToken());
        assertNull(session.getRefreshToken());
        assertFalse(session.isLoggedIn());
        InOrder order = inOrder(push, store, farm, cleaner, database);
        order.verify(push).clearLocalBinding();
        order.verify(store).clear();
        order.verify(farm).clear();
        order.verify(cleaner).stopPendingWork();
        order.verify(database).clearAllTables();
        order.verify(cleaner).clear();
        verify(push, never()).unbindCurrent();
    }

    @Test public void sdkFailure_doesNotKeepCancelledAccountLoggedIn() {
        doThrow(new IllegalStateException("SDK unavailable")).when(push).clearLocalBinding();
        assertTrue(session.clearAfterAccountCancellation(7));
        assertFalse(session.isLoggedIn());
        assertNull(session.currentUser());
        verify(database).clearAllTables();
        verify(cleaner).clear();
    }

    @Test public void delayedCancellationOfA_cannotClearNewAccountBOrItsPush() {
        AuthTokenVO next = mock(AuthTokenVO.class);
        when(next.getAccessToken()).thenReturn("new-access");
        when(next.getRefreshToken()).thenReturn("new-refresh");
        when(next.getUser()).thenReturn(new AccountUserVO(8, null, null, null));
        session.saveToken(next);
        assertFalse(session.clearAfterAccountCancellation(7));
        assertEquals(8, session.currentUser().getUserId());
        assertEquals("new-access", session.getAccessToken());
        assertTrue(session.isLoggedIn());
        verify(store, never()).clear();
        verifyNoInteractions(push, farm, database, cleaner);
    }

    @Test public void successfulCancellation_stillCleansDataIfSameSessionWasAlreadyCleared() {
        session.logout();
        assertTrue(session.clearAfterAccountCancellation(7));
        verify(push).clearLocalBinding();
        verify(cleaner).clear();
    }

    @Test public void queuedOldSessionDatabaseClear_cannotEraseNewAccountDrafts() {
        TestScheduler pending = new TestScheduler();
        RxJavaPlugins.setIoSchedulerHandler(s -> pending);
        session.logout();
        AuthTokenVO next = mock(AuthTokenVO.class);
        when(next.getAccessToken()).thenReturn("new-access");
        when(next.getRefreshToken()).thenReturn("new-refresh");
        when(next.getUser()).thenReturn(new AccountUserVO(8, null, null, null));
        session.saveToken(next);
        pending.triggerActions();
        verify(database, never()).clearAllTables();
        assertEquals(8, session.currentUser().getUserId());
    }

    @Test public void ordinaryLogout_doesNotDeletePersonalPreferencesOrRepeatPushUnbind() {
        session.logout();
        assertFalse(session.isLoggedIn());
        verifyNoInteractions(push, cleaner);
        verify(database).clearAllTables();
    }
}
