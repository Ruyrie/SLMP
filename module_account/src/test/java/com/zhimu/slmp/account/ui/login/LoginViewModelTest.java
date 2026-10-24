package com.zhimu.slmp.account.ui.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.account.model.request.SmsLoginRequest;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.DeviceInfoProvider;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.session.AppSettings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * A01 LoginViewModel：表单校验、canSubmit、提交状态与错误写入。
 */
public class LoginViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private AccountRepository repository;
    private AppSettings settings;
    private LoginViewModel vm;

    @Before
    public void setUp() {
        repository = mock(AccountRepository.class);
        DeviceInfoProvider device = mock(DeviceInfoProvider.class);
        when(device.get()).thenReturn(new DeviceInfo("install-1", "Pixel", "Android 16"));
        settings = mock(AppSettings.class);
        vm = new LoginViewModel(repository, device, settings);
        vm.attachSavedState(new SavedStateHandle());
        vm.getCanSubmit().observeForever(v -> {
        });
    }

    @Test
    public void emptyPhone_showsRequiredError_andDoesNotCallApi() {
        vm.password.setValue("Zmt12345");
        vm.login();
        Map<String, String> errors = vm.getFieldErrors().getValue();
        assertEquals(FormValidator.MSG_PHONE_EMPTY, errors.get(LoginViewModel.FIELD_PHONE));
        assertEquals(LoginViewModel.FIELD_PHONE, vm.getFocusField().getValue());
        verify(repository, never()).loginByPassword(any());
    }

    @Test
    public void invalidPhone_showsFormatError() {
        vm.phone.setValue("138 0471");
        vm.password.setValue("Zmt12345");
        vm.login();
        assertEquals(FormValidator.MSG_PHONE_INVALID, vm.getFieldErrors().getValue().get(LoginViewModel.FIELD_PHONE));
    }

    @Test
    public void canSubmit_followsRequiredFieldsOfCurrentMode() {
        assertFalse(vm.getCanSubmit().getValue());
        vm.phone.setValue("138 0047 6620");
        assertFalse(vm.getCanSubmit().getValue());
        vm.password.setValue("x");
        assertTrue(vm.getCanSubmit().getValue());
        vm.switchMode(LoginViewModel.MODE_SMS);
        assertFalse(vm.getCanSubmit().getValue());
        vm.smsCode.setValue("123456");
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void smsMode_requiresSixDigitCode() {
        vm.switchMode(LoginViewModel.MODE_SMS);
        vm.phone.setValue("138 0047 6620");
        vm.smsCode.setValue("1234");
        vm.login();
        assertEquals(FormValidator.MSG_SMS_CODE_INVALID, vm.getFieldErrors().getValue().get(LoginViewModel.FIELD_SMS_CODE));
        verify(repository, never()).loginBySms(any());
    }

    @Test
    public void passwordLoginSuccess_restoresFarmAndEmitsLoginSuccess() {
        AuthTokenVO token = TestData.fromJson(TestData.TOKEN_OWNER, AuthTokenVO.class);
        when(repository.loginByPassword(any())).thenReturn(Single.just(token));
        when(repository.restoreFarm(1001L)).thenReturn(Completable.complete());
        vm.phone.setValue("138 0047 6620");
        vm.password.setValue("Zmt12345");

        vm.login();

        ArgumentCaptor<PasswordLoginRequest> req = ArgumentCaptor.forClass(PasswordLoginRequest.class);
        verify(repository).loginByPassword(req.capture());
        assertEquals("13800476620", req.getValue().getPhone());
        assertEquals("install-1", req.getValue().getDeviceId());
        verify(repository).restoreFarm(1001L);
        verify(settings).setLastPhone("13800476620");
        assertSame(token, vm.getLoginSuccess().getValue());
        assertFalse(vm.isSubmitting());
    }

    @Test
    public void smsLogin_sendsTrimmedCode() {
        AuthTokenVO token = TestData.fromJson(TestData.TOKEN_OWNER, AuthTokenVO.class);
        when(repository.loginBySms(any())).thenReturn(Single.just(token));
        when(repository.restoreFarm(anyLong())).thenReturn(Completable.complete());
        vm.switchMode(LoginViewModel.MODE_SMS);
        vm.phone.setValue("137 0000 5678");
        vm.smsCode.setValue("123456");

        vm.login();

        ArgumentCaptor<SmsLoginRequest> req = ArgumentCaptor.forClass(SmsLoginRequest.class);
        verify(repository).loginBySms(req.capture());
        assertEquals("13700005678", req.getValue().getPhone());
        assertEquals("123456", req.getValue().getSmsCode());
    }

    @Test
    public void smsLogin_farmLoadFailure_staysOnLoginAndRetriesWithoutConsumingCodeAgain() {
        AuthTokenVO token = TestData.fromJson(TestData.TOKEN_OWNER, AuthTokenVO.class);
        when(repository.loginBySms(any())).thenReturn(Single.just(token));
        when(repository.restoreFarm(1001L)).thenReturn(Completable.error(ApiException.network(null)),
                Completable.complete());
        vm.switchMode(LoginViewModel.MODE_SMS);
        vm.phone.setValue("13700005678");
        vm.smsCode.setValue("123456");

        vm.login();

        assertNull(vm.getLoginSuccess().getValue());
        assertFalse(vm.isSubmitting());
        assertTrue(vm.getFarmRestoreFailed().getValue());
        assertEquals(LoginViewModel.MSG_FARM_RESTORE_FAILED, vm.getBanner().getValue());
        assertEquals(LoginViewModel.ACTION_RETRY_LOGIN, vm.getMessage().getValue().getActionId());

        vm.login();

        verify(repository, times(1)).loginBySms(any());
        verify(repository, times(2)).restoreFarm(1001L);
        assertSame(token, vm.getLoginSuccess().getValue());
        assertFalse(vm.getFarmRestoreFailed().getValue());
        assertNull(vm.getBanner().getValue());
    }

    @Test
    public void changingCredentialsAfterFarmLoadFailure_authenticatesAgain() {
        AuthTokenVO token = TestData.fromJson(TestData.TOKEN_OWNER, AuthTokenVO.class);
        when(repository.loginByPassword(any())).thenReturn(Single.just(token));
        when(repository.restoreFarm(1001L)).thenReturn(Completable.error(ApiException.network(null)),
                Completable.complete());
        vm.phone.setValue("13800476620");
        vm.password.setValue("Zmt12345");
        vm.login();

        vm.password.setValue("NewPass123");

        assertFalse(vm.getFarmRestoreFailed().getValue());
        assertNull(vm.getBanner().getValue());
        vm.login();
        verify(repository, times(2)).loginByPassword(any());
        assertSame(token, vm.getLoginSuccess().getValue());
    }

    @Test
    public void wrongPassword_writesErrorToPasswordField_andKeepsPassword() {
        when(repository.loginByPassword(any())).thenReturn(Single.error(
                new ApiException(ErrorCodes.PASSWORD_WRONG, "手机号或密码不正确")));
        vm.phone.setValue("138 0047 6620");
        vm.password.setValue("wrong123");

        vm.login();

        assertEquals("手机号或密码不正确", vm.getFieldErrors().getValue().get(LoginViewModel.FIELD_PASSWORD));
        assertEquals("wrong123", vm.password.getValue());
        assertFalse(vm.isSubmitting());
        assertNull(vm.getLoginSuccess().getValue());
    }

    @Test
    public void networkError_showsKeepContentMessage() {
        when(repository.loginByPassword(any())).thenReturn(Single.error(ApiException.network(null)));
        vm.phone.setValue("138 0047 6620");
        vm.password.setValue("Zmt12345");
        vm.login();
        assertEquals(AccountForm.MSG_NETWORK, vm.getMessage().getValue().getText());
    }

    @Test
    public void reloginReason_showsBanner() {
        vm.applyLogoutReason(LoginViewModel.REASON_PASSWORD_CHANGED);
        assertEquals(LoginViewModel.BANNER_RELOGIN, vm.getBanner().getValue());
        vm.applyLogoutReason("EXPIRED");
        assertNull(vm.getBanner().getValue());
    }

    @Test
    public void passwordReset_switchesToPasswordModeAndClearsPassword() {
        vm.switchMode(LoginViewModel.MODE_SMS);
        vm.password.setValue("old");
        vm.onPasswordReset();
        assertEquals(LoginViewModel.MODE_PASSWORD, vm.getLoginMode().getValue());
        assertEquals("", vm.password.getValue());
        assertEquals(LoginViewModel.MSG_PASSWORD_RESET, vm.getMessage().getValue().getText());
    }
}
