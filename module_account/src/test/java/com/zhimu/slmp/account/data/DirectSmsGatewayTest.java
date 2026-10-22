package com.zhimu.slmp.account.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.model.request.AccountCancelRequest;
import com.zhimu.slmp.account.model.request.AccountRecoveryRequest;
import com.zhimu.slmp.account.model.request.PasswordResetRequest;
import com.zhimu.slmp.account.model.request.RecoveryCodeRegenerateRequest;
import com.zhimu.slmp.account.model.vo.SmsCodeSendVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.session.AppSettings;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.common.sms.AliyunSmsClient;
import com.zhimu.slmp.common.sms.AliyunSmsErrors;
import com.zhimu.slmp.common.sms.SmsSendResult;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 短信验证码测试直连：场景 → 模板、A02 本人手机号匹配、先校验再提交（含关闭时原样提交）。
 */
public class DirectSmsGatewayTest {

    @Rule
    public final RxTestRule rx = new RxTestRule();

    private AliyunSmsClient client;
    private SessionManager session;
    private AppSettings settings;
    private final List<String> backendCodes = new ArrayList<>();

    @Before
    public void setUp() {
        client = mock(AliyunSmsClient.class);
        session = mock(SessionManager.class);
        settings = mock(AppSettings.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.checkVerifyCode(anyString(), anyString())).thenReturn(Completable.complete());
    }

    private DirectSmsGateway gateway(boolean mockBackend) {
        return new DirectSmsGateway(client, session, settings, mockBackend);
    }

    private Single<String> record(String code) {
        backendCodes.add(code);
        return Single.just("ok");
    }

    // ===== 场景 → 模板 =====

    @Test
    public void templateFor_scenes() {
        assertEquals("100001", DirectSmsGateway.templateFor("LOGIN"));
        assertEquals("100001", DirectSmsGateway.templateFor("REGISTER"));
        assertEquals("100003", DirectSmsGateway.templateFor("RESET_PASSWORD"));
        assertEquals("100004", DirectSmsGateway.templateFor("RECOVERY_NEW_PHONE"));
        assertEquals("100005", DirectSmsGateway.templateFor("REGENERATE_RECOVERY_CODE"));
        assertEquals("100005", DirectSmsGateway.templateFor("CANCEL_ACCOUNT"));
        assertNull(DirectSmsGateway.templateFor("CHANGE_PHONE"));
        assertNull(DirectSmsGateway.templateFor(null));
    }

    // ===== 发送 =====

    @Test
    public void send_returnsSameUiDataAsApi() {
        when(client.sendVerifyCode("13800006620", "100001"))
                .thenReturn(Single.just(new SmsSendResult("BIZ", "REQ", 60, 300)));
        SmsCodeSendVO vo = gateway(true).send("13800006620", "LOGIN").blockingGet();
        assertEquals("138****6620", vo.getPhoneMasked());
        assertEquals(60, vo.getCooldownSeconds());
        assertEquals(300, vo.getExpireSeconds());
    }

    @Test
    public void send_unknownScene_fails() {
        try {
            gateway(true).send("13800006620", "CHANGE_PHONE").blockingGet();
            fail();
        } catch (ApiException e) {
            assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        }
    }

    // ===== A02 本人手机号 =====

    @Test
    public void accountPhone_matchesMaskedPhone() {
        when(session.currentUser()).thenReturn(new AccountUserVO(10001, "王建国", "王", "138****6620"));
        when(settings.getLastPhone()).thenReturn("138 0000 6620");
        assertEquals("13800006620", gateway(true).accountPhone());
    }

    @Test
    public void accountPhone_mismatch_isNull() {
        when(session.currentUser()).thenReturn(new AccountUserVO(10001, "王建国", "王", "138****6620"));
        when(settings.getLastPhone()).thenReturn("13912345678");
        assertNull(gateway(true).accountPhone());
    }

    @Test
    public void accountPhone_missingData_isNull() {
        when(settings.getLastPhone()).thenReturn("13800006620");
        assertNull(gateway(true).accountPhone());
        when(session.currentUser()).thenReturn(new AccountUserVO(10001, "王建国", "王", "138****6620"));
        when(settings.getLastPhone()).thenReturn(null);
        assertNull(gateway(true).accountPhone());
    }

    @Test
    public void accountPhone_disabled_isNull() {
        when(client.isEnabled()).thenReturn(false);
        when(session.currentUser()).thenReturn(new AccountUserVO(10001, "王建国", "王", "138****6620"));
        when(settings.getLastPhone()).thenReturn("13800006620");
        assertNull(gateway(true).accountPhone());
    }

    // ===== 先校验再提交 =====

    @Test
    public void verifyThen_disabled_submitsOriginalCodeWithoutCheck() {
        when(client.isEnabled()).thenReturn(false);
        gateway(true).verifyThen("13800006620", "482913", this::record).blockingGet();
        assertEquals("482913", backendCodes.get(0));
        verify(client, never()).checkVerifyCode(anyString(), anyString());
    }

    @Test
    public void verifyThen_nullPhone_submitsOriginalCodeWithoutCheck() {
        gateway(true).verifyThen(null, "482913", this::record).blockingGet();
        assertEquals("482913", backendCodes.get(0));
        verify(client, never()).checkVerifyCode(anyString(), anyString());
    }

    @Test
    public void verifyThen_pass_mockBackend_submitsMockCode() {
        gateway(true).verifyThen("13800006620", "482913", this::record).blockingGet();
        verify(client).checkVerifyCode("13800006620", "482913");
        assertEquals(DirectSmsGateway.MOCK_SMS_CODE, backendCodes.get(0));
    }

    @Test
    public void verifyThen_pass_realBackend_submitsOriginalCode() {
        gateway(false).verifyThen("13800006620", "482913", this::record).blockingGet();
        assertEquals("482913", backendCodes.get(0));
    }

    @Test
    public void verifyThen_wrongCode_fails40103WithoutSubmit() {
        when(client.checkVerifyCode("13800006620", "000000"))
                .thenReturn(Completable.error(AliyunSmsErrors.verifyFailed("REQ")));
        try {
            gateway(true).verifyThen("13800006620", "000000", this::record).blockingGet();
            fail();
        } catch (ApiException e) {
            assertEquals(ErrorCodes.SMS_CODE_WRONG, e.getCode());
        }
        assertEquals(0, backendCodes.size());
    }

    @Test
    public void verifyThen_retryAfterPass_doesNotCheckAgain() {
        DirectSmsGateway g = gateway(true);
        g.verifyThen("13800006620", "482913", this::record).blockingGet();
        g.verifyThen("13800006620", "482913", this::record).blockingGet();
        verify(client, times(1)).checkVerifyCode("13800006620", "482913");
        assertEquals(2, backendCodes.size());
    }

    @Test
    public void verifyThen_resendClearsVerifiedCode() {
        when(client.sendVerifyCode(anyString(), anyString()))
                .thenReturn(Single.just(new SmsSendResult("BIZ", "REQ", 60, 300)));
        DirectSmsGateway g = gateway(true);
        g.verifyThen("13800006620", "482913", this::record).blockingGet();
        g.send("13800006620", "LOGIN").blockingGet();
        g.verifyThen("13800006620", "482913", this::record).blockingGet();
        verify(client, times(2)).checkVerifyCode("13800006620", "482913");
    }

    @Test
    public void verifyThenComplete_passesBackendCode() {
        List<String> codes = new ArrayList<>();
        gateway(true).verifyThenComplete("13800006620", "482913", c -> {
            codes.add(c);
            return Completable.complete();
        }).blockingAwait();
        assertEquals(DirectSmsGateway.MOCK_SMS_CODE, codes.get(0));
    }

    // ===== 请求副本 =====

    @Test
    public void withSmsCode_copiesOtherFields() {
        PasswordResetRequest reset = new PasswordResetRequest("13800006620", "482913", "Zmt12345");
        assertSame(reset, reset.withSmsCode("482913"));
        PasswordResetRequest r2 = reset.withSmsCode("123456");
        assertEquals("123456", r2.getSmsCode());
        assertEquals("Zmt12345", r2.getNewPassword());

        AccountRecoveryRequest rec = new AccountRecoveryRequest("13800006620", "8KQ4T2MW7HXN3PDR", "15147602288",
                "482913", "说明").withNewPhoneSmsCode("123456");
        assertEquals("123456", rec.getNewPhoneSmsCode());
        assertEquals("15147602288", rec.getNewPhone());
        assertEquals("8KQ4T2MW7HXN3PDR", rec.getRecoveryCode());

        AccountCancelRequest cancel = new AccountCancelRequest("482913", "v3", true).withSmsCode("123456");
        assertEquals("123456", cancel.getSmsCode());
        assertEquals("v3", cancel.getChecklistVersion());

        assertEquals("123456", new RecoveryCodeRegenerateRequest("482913").withSmsCode("123456").getSmsCode());
    }
}
