package com.zhimu.slmp.today.push;

import android.content.Context;

import com.google.gson.Gson;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.today.RxTestRule;
import com.zhimu.slmp.today.data.PushDeviceRepository;
import com.zhimu.slmp.today.model.request.PushDeviceRequest;
import com.zhimu.slmp.today.model.vo.PushDeviceVO;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.Assert.assertFalse;

public class PushDeviceManagerTest {

    @Rule
    public RxTestRule rx = new RxTestRule();

    private Context context;
    private SessionManager session;
    private PushDeviceRepository repository;
    private PushDeviceManager manager;

    @Before
    public void setUp() throws Exception {
        context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        session = mock(SessionManager.class);
        when(session.isLoggedIn()).thenReturn(true);
        when(session.currentUser()).thenReturn(new AccountUserVO(7L, null, null, null));
        repository = mock(PushDeviceRepository.class);
        manager = spy(new PushDeviceManager(context, repository, session));
        Field ready = PushDeviceManager.class.getDeclaredField("jpushReady");
        ready.setAccessible(true);
        ready.set(manager, true);
        doReturn(new PushDeviceRequest("registration7", "Google", "Pixel 9 Pro", "Android 16", "1.0.0", true))
                .when(manager).currentDevice();
        doNothing().when(manager).resumeLocalPush();
        doNothing().when(manager).deleteLocalAlias();
        doNothing().when(manager).cleanLocalTags();
        doNothing().when(manager).clearLocalNotifications();
        doNothing().when(manager).stopLocalPush();
    }

    @Test
    public void aliasBindingFailure_isRetriedOnNextSync() {
        when(repository.register(any())).thenReturn(Single.just(response(false)));
        withSdk(() -> {
            manager.syncDevice();
            manager.syncDevice();
        });
        verify(repository, times(2)).register(any());
    }

    @Test
    public void successfulBinding_isCachedForSameUser() {
        when(repository.register(any())).thenReturn(Single.just(response(true)));
        withSdk(() -> {
            manager.syncDevice();
            manager.syncDevice();
        });
        verify(repository).register(any());
    }

    @Test
    public void changedUser_mustRegisterEvenWhenDeviceIsUnchanged() {
        when(repository.register(any())).thenReturn(Single.just(response(true)));
        withSdk(() -> {
            manager.syncDevice();
            when(session.currentUser()).thenReturn(new AccountUserVO(8L, null, null, null));
            manager.syncDevice();
        });
        verify(repository, times(2)).register(any());
    }

    @Test
    public void simultaneousLoginAndForegroundSync_onlyRegisterOnce() {
        SingleSubject<PushDeviceVO> pending = SingleSubject.create();
        when(repository.register(any())).thenReturn(pending);
        withSdk(() -> {
            manager.syncDevice();
            manager.syncDevice();
            pending.onSuccess(response(true));
            manager.syncDevice();
        });
        verify(repository).register(any());
    }

    @Test
    public void networkFailure_isRetriedOnNextSync() {
        when(repository.register(any())).thenReturn(Single.error(new IllegalStateException("offline")));
        withSdk(() -> {
            manager.syncDevice();
            manager.syncDevice();
        });
        verify(repository, times(2)).register(any());
    }

    @Test public void cancellation_disposesPendingRegistrationAndCannotRestoreOldBinding() {
        SingleSubject<PushDeviceVO> pending = SingleSubject.create();
        when(repository.register(any())).thenReturn(pending);
        manager.syncDevice();
        manager.clearLocalBinding();
        assertFalse(pending.hasObservers());
        pending.onSuccess(response(true));
        manager.syncDevice(); // Even before SessionManager finishes, the cancelled user cannot re-register.
        verify(repository).register(any());
        verify(repository, never()).unbind(any(), any());
        verify(manager).deleteLocalAlias();
        verify(manager).cleanLocalTags();
        verify(manager).clearLocalNotifications();
        verify(manager).stopLocalPush();
    }

    @Test public void cancellation_sdkAliasFailureStillClearsTagsNotificationsAndStopsChannel() {
        doThrow(new IllegalStateException("SDK alias failure")).when(manager).deleteLocalAlias();
        manager.clearLocalBinding();
        verify(manager).cleanLocalTags();
        verify(manager).clearLocalNotifications();
        verify(manager).stopLocalPush();
        verifyNoInteractions(repository);
    }

    @Test public void anotherAccountCanRegisterAfterCancellationWithoutReusingOldCache() {
        when(repository.register(any())).thenReturn(Single.just(response(true)));
        manager.syncDevice();
        manager.clearLocalBinding();
        when(session.currentUser()).thenReturn(new AccountUserVO(8, null, null, null));
        manager.syncDevice();
        manager.syncDevice();
        verify(repository, times(2)).register(any());
    }

    private void withSdk(Runnable action) {
        action.run();
    }

    private static PushDeviceVO response(boolean bound) {
        return new Gson().fromJson("{\"aliasBound\":" + bound + "}", PushDeviceVO.class);
    }
}
