package com.zhimu.slmp.account.ui.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.zhimu.slmp.account.model.request.RegisterRequest;
import com.zhimu.slmp.account.model.vo.AgreementVO;
import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.account.util.DeviceInfoProvider;
import com.zhimu.slmp.account.util.RecoveryCodeHolder;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.router.RouterPath;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * A02 RegisterViewModel：表单校验（手机号、验证码、新密码两条文案、协议勾选）、提交状态、幂等键与验证码倒计时。
 */
public class RegisterViewModelTest {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private AccountRepository repository;
    private RecoveryCodeHolder holder;
    private RegisterViewModel vm;

    @Before
    public void setUp() {
        repository = mock(AccountRepository.class);
        when(repository.getAgreement()).thenReturn(Single.just(TestData.fromJson(TestData.AGREEMENT, AgreementVO.class)));
        DeviceInfoProvider device = mock(DeviceInfoProvider.class);
        when(device.get()).thenReturn(new DeviceInfo("install-1", null, null));
        holder = new RecoveryCodeHolder();
        vm = new RegisterViewModel(repository, device, holder);
        vm.attachSavedState(new SavedStateHandle());
        vm.getCanSubmit().observeForever(v -> {
        });
    }

    private void fillValid() {
        vm.phone.setValue("136 0000 1111");
        vm.smsCode.setValue("123456");
        vm.password.setValue("Zmt12345");
        vm.agreeTerms.setValue(true);
    }

    @Test
    public void loadsAgreementOnStart() {
        assertEquals("2026-10-01", vm.getAgreement().getValue().getAgreementVersion());
    }

    @Test
    public void passwordRules_haveSeparateMessages() {
        fillValid();
        vm.password.setValue("abc1");
        assertEquals(AccountForm.MSG_PASSWORD_LENGTH, vm.validate().get(RegisterViewModel.FIELD_PASSWORD));
        vm.password.setValue("abcdefgh");
        assertEquals(AccountForm.MSG_PASSWORD_MIX, vm.validate().get(RegisterViewModel.FIELD_PASSWORD));
        vm.password.setValue("12345678");
        assertEquals(AccountForm.MSG_PASSWORD_MIX, vm.validate().get(RegisterViewModel.FIELD_PASSWORD));
        vm.password.setValue("Zmt12345");
        assertNull(vm.validate().get(RegisterViewModel.FIELD_PASSWORD));
    }

    @Test
    public void mustAgreeTerms() {
        fillValid();
        vm.agreeTerms.setValue(false);
        vm.register();
        Map<String, String> errors = vm.getFieldErrors().getValue();
        assertEquals(AccountForm.MSG_AGREE_TERMS, errors.get(RegisterViewModel.FIELD_AGREE_TERMS));
        verify(repository, never()).register(any(), anyString());
    }

    @Test
    public void canSubmit_needsThreeInputs() {
        assertFalse(vm.getCanSubmit().getValue());
        vm.phone.setValue("136 0000 1111");
        vm.smsCode.setValue("123456");
        assertFalse(vm.getCanSubmit().getValue());
        vm.password.setValue("x");
        assertTrue(vm.getCanSubmit().getValue());
    }

    @Test
    public void registerSuccess_holdsRecoveryCodeAndOpensA04() {
        AuthTokenVO token = TestData.fromJson(TestData.TOKEN_REGISTER, AuthTokenVO.class);
        when(repository.register(any(), anyString())).thenReturn(Single.just(token));
        fillValid();

        vm.register();

        ArgumentCaptor<RegisterRequest> req = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(repository).register(req.capture(), anyString());
        assertEquals("13600001111", req.getValue().getPhone());
        assertEquals("2026-10-01", req.getValue().getAgreementVersion());
        assertTrue(req.getValue().isAgreeTerms());
        assertEquals(RouterPath.A04, vm.getNavigate().getValue());
        assertEquals("8KQ4T2MW7HXN3PDR", holder.take().getCode());
        assertNull(vm.getIdemKey());
        assertFalse(vm.isSubmitting());
    }

    @Test
    public void networkFailure_keepsIdemKeyForRetry() {
        when(repository.register(any(), anyString())).thenReturn(Single.error(ApiException.network(null)));
        fillValid();

        vm.register();
        String first = vm.getIdemKey();
        assertNotNull(first);
        vm.register();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(repository, times(2)).register(any(), keys.capture());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertEquals(AccountForm.MSG_NETWORK, vm.getMessage().getValue().getText());
    }

    @Test
    public void businessError_resetsIdemKey_andPhoneRegisteredGoesToPhoneField() {
        when(repository.register(any(), anyString())).thenReturn(Single.error(
                new ApiException(ErrorCodes.PHONE_REGISTERED, "这个手机号已经注册，可以直接登录")));
        fillValid();

        vm.register();

        assertNull(vm.getIdemKey());
        assertEquals("这个手机号已经注册，可以直接登录", vm.getFieldErrors().getValue().get(RegisterViewModel.FIELD_PHONE));
        assertEquals(RegisterViewModel.ACTION_LOGIN, vm.getMessage().getValue().getActionId());
    }

    @Test
    public void sendSmsCode_startsCountdown() {
        SmsCodeSendVO sent = TestData.fromJson(TestData.SMS_SENT, SmsCodeSendVO.class);
        when(repository.sendSmsCode(any())).thenReturn(Single.just(sent));
        vm.phone.setValue("136 0000 1111");

        vm.sendSmsCode();

        assertEquals(Integer.valueOf(60), vm.getSmsCountdown().getValue());
        assertEquals("验证码已发送至 138****6620", vm.getSmsSentHint().getValue());
        rx.computation().advanceTimeBy(8, TimeUnit.SECONDS);
        assertEquals(Integer.valueOf(52), vm.getSmsCountdown().getValue());
        vm.sendSmsCode();
        verify(repository, times(1)).sendSmsCode(any());
    }
}
