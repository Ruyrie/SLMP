package com.zhimu.slmp.account.data;

import android.content.Context;
import com.google.gson.JsonElement;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.model.request.AccountCancelRequest;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.common.sms.AliyunSmsClient;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AccountCancellationCleanupTest {
    @Rule public RxTestRule rx = new RxTestRule();
    private AccountApi api;
    private SessionManager session;
    private AccountRepository repository;
    private IPushService previousPush;
    private IPushService push;
    private final AccountCancelRequest request = new AccountCancelRequest("123456", "qa-preview", true);

    @Before public void setUp() {
        api = mock(AccountApi.class);
        session = mock(SessionManager.class);
        when(session.currentUser()).thenReturn(new AccountUserVO(7, null, null, null));
        when(session.clearAfterAccountCancellation(7)).thenReturn(true);
        DirectSmsGateway sms = new DirectSmsGateway(mock(AliyunSmsClient.class), session, mock(AppSettings.class), false);
        repository = new AccountRepository(api, api, session, mock(FarmContext.class), sms, mock(Context.class));
        previousPush = ServiceHub.get(IPushService.class);
        push = mock(IPushService.class);
        ServiceHub.register(IPushService.class, push);
    }

    @After public void restoreService() {
        ServiceHub.register(IPushService.class, previousPush == null ? mock(IPushService.class) : previousPush);
    }

    @Test public void cancellation_waitsForApiSuccessBeforeLocalCleanup() {
        SingleSubject<ApiResponse<JsonElement>> pending = SingleSubject.create();
        when(api.cancelAccount(any(), anyString())).thenReturn(pending);
        TestObserver<Void> result = repository.cancelAccount(request, "cancel-qa").test();
        verify(session, never()).clearAfterAccountCancellation(anyLong());
        pending.onSuccess(new ApiResponse<>(0, "ok", null, null));
        result.assertComplete().assertNoErrors();
        verify(session).clearAfterAccountCancellation(7);
        verify(session, never()).logout();
        verifyNoInteractions(push);
        verify(api, never()).logout();
    }

    @Test public void cancellationFailure_keepsAllLocalDataAndPushBinding() {
        when(api.cancelAccount(any(), anyString())).thenReturn(Single.just(
                new ApiResponse<>(ErrorCodes.SMS_CODE_WRONG, "验证码不正确", null, null)));
        repository.cancelAccount(request, "cancel-qa").test().assertError(ApiException.class);
        verify(session, never()).clearAfterAccountCancellation(anyLong());
        verify(session, never()).logout();
        verifyNoInteractions(push);
    }

    @Test public void cancellationCallback_keepsCapturedUserAndDoesNotNavigateAnotherUserToLogin() {
        SingleSubject<ApiResponse<JsonElement>> pending = SingleSubject.create();
        when(api.cancelAccount(any(), anyString())).thenReturn(pending);
        TestObserver<Void> result = repository.cancelAccount(request, "cancel-qa").test();
        when(session.currentUser()).thenReturn(new AccountUserVO(8, null, null, null));
        when(session.clearAfterAccountCancellation(7)).thenReturn(false);
        pending.onSuccess(new ApiResponse<>(0, "ok", null, null));
        result.assertError(t -> t instanceof ApiException && ((ApiException) t).getCode() == ErrorCodes.UNKNOWN);
        verify(session).clearAfterAccountCancellation(7);
        verify(session, never()).clearAfterAccountCancellation(8);
        verify(session, never()).logout();
    }

    @Test public void normalLogout_preservesPushThenRemoteThenLocalOrder() {
        List<String> calls = new ArrayList<>();
        doAnswer(call -> { calls.add("push"); return null; }).when(push).unbindCurrent();
        when(api.logout()).thenReturn(Single.defer(() -> {
            calls.add("logout-api"); return Single.just(new ApiResponse<>(0, "ok", null, null));
        }));
        doAnswer(call -> { calls.add("local-session"); return null; }).when(session).logout();
        repository.logoutAndClear().test().assertComplete().assertNoErrors();
        assertEquals(Arrays.asList("push", "logout-api", "local-session"), calls);
        verify(push, never()).clearLocalBinding();
        verify(session, never()).clearAfterAccountCancellation(anyLong());
    }
}
