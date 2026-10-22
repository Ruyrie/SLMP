package com.zhimu.slmp.common.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;

import org.junit.Test;

/**
 * 阿里云返回码 → ApiException 的映射。
 */
public class AliyunSmsErrorsTest {

    @Test
    public void frequencyCodes_map42901WithCooldown() {
        for (String code : new String[]{"biz.FREQUENCY", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL",
                "BUSINESS_LIMIT_CONTROL", "LimitExceeded.SmsCode", "isp.QPS_LIMIT", "Throttling.User"}) {
            ApiException e = AliyunSmsErrors.toApiException(code, "Check frequency fail.", "REQ-1", 42);
            assertEquals(code, ErrorCodes.TOO_FREQUENT, e.getCode());
            assertEquals("操作太频繁，请稍后再试", e.getMessage());
            assertEquals("42", e.dataString("cooldown_seconds"));
            assertEquals(code, e.dataString("provider_code"));
            assertEquals("REQ-1", e.getTraceId());
        }
    }

    @Test
    public void frequency_withoutCooldown_hasNoCooldownField() {
        ApiException e = AliyunSmsErrors.toApiException("isp.QPS_LIMIT", null, "REQ-2", 0);
        assertEquals(ErrorCodes.TOO_FREQUENT, e.getCode());
        assertNull(e.dataString("cooldown_seconds"));
    }

    @Test
    public void validateFail_maps40103() {
        ApiException e = AliyunSmsErrors.toApiException("isv.ValidateFail", "check fail", "REQ-3", 0);
        assertEquals(ErrorCodes.SMS_CODE_WRONG, e.getCode());
        assertEquals("验证码不正确或已过期，请重新获取", e.getMessage());
        assertEquals("REQ-3", e.getTraceId());
    }

    @Test
    public void verifyFailed_maps40103() {
        ApiException e = AliyunSmsErrors.verifyFailed("REQ-4");
        assertEquals(ErrorCodes.SMS_CODE_WRONG, e.getCode());
        assertEquals(AliyunSmsErrors.MSG_CODE_WRONG, e.getMessage());
        assertEquals("REQ-4", e.getTraceId());
    }

    @Test
    public void phoneIllegal_maps40001() {
        for (String code : new String[]{"MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_NUMBER_ILLEGAL", "InvalidPhoneNumber.Check"}) {
            ApiException e = AliyunSmsErrors.toApiException(code, "The mobile number is illegal.", "REQ-5", 0);
            assertEquals(ErrorCodes.PARAM_INVALID, e.getCode());
            assertEquals("手机号格式不正确，请输入 11 位手机号", e.getMessage());
            assertTrue(e.getFieldErrors().isEmpty());
        }
    }

    @Test
    public void otherCodes_map50301WithProviderMessageAndTrace() {
        ApiException e = AliyunSmsErrors.toApiException("SignatureDoesNotMatch",
                "Specified signature is not matched with our calculation.", "REQ-6", 30);
        assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        assertEquals("短信服务调用失败：Specified signature is not matched with our calculation.（SignatureDoesNotMatch）",
                e.getMessage());
        assertEquals("REQ-6", e.getTraceId());
        assertEquals("SignatureDoesNotMatch", e.dataString("provider_code"));
        assertNull(e.dataString("cooldown_seconds"));
    }

    @Test
    public void otherCodes_emptyMessage() {
        ApiException e = AliyunSmsErrors.toApiException("FUNCTION_NOT_OPENED", " ", null, 0);
        assertEquals(ErrorCodes.SERVICE_UNAVAILABLE, e.getCode());
        assertEquals("短信服务调用失败：未知错误（FUNCTION_NOT_OPENED）", e.getMessage());
    }
}
