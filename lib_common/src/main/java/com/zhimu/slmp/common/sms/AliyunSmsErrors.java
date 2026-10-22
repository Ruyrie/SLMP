package com.zhimu.slmp.common.sms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 阿里云返回码 → 统一业务错误 ApiException（trace_id 为阿里云 RequestId，data.provider_code 为原返回码）：
 * <ul>
 * <li>频率与日上限（biz.FREQUENCY、FREQUENCY_FAIL、isv.BUSINESS_LIMIT_CONTROL、LimitExceeded.SmsCode、isp.QPS_LIMIT、Throttling*）
 * → 42901“操作太频繁，请稍后再试”，已知剩余间隔时带 data.cooldown_seconds；</li>
 * <li>验证码错误或过期（isv.ValidateFail，或 CheckSmsVerifyCode 的 VerifyResult 不是 PASS）
 * → 40103“验证码不正确或已过期，请重新获取”；</li>
 * <li>手机号格式错误（MOBILE_NUMBER_ILLEGAL 等）→ 40001“手机号格式不正确，请输入 11 位手机号”；</li>
 * <li>其他（签名错误、Key 无效、未开通、参数错误等）→ 50301，文案为阿里云返回的说明与返回码，便于排查。</li>
 * </ul>
 */
public final class AliyunSmsErrors {

    public static final String MSG_TOO_FREQUENT = "操作太频繁，请稍后再试";
    public static final String MSG_CODE_WRONG = "验证码不正确或已过期，请重新获取";
    public static final String MSG_PHONE_INVALID = "手机号格式不正确，请输入 11 位手机号";
    public static final String MSG_FAILED_PREFIX = "短信服务调用失败：";

    /** data 中的阿里云原返回码 */
    public static final String KEY_PROVIDER_CODE = "provider_code";
    public static final String KEY_COOLDOWN = "cooldown_seconds";

    private static final Set<String> FREQUENCY = new HashSet<>(Arrays.asList(
            "biz.FREQUENCY", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL", "BUSINESS_LIMIT_CONTROL",
            "isv.DAY_LIMIT_CONTROL", "LimitExceeded.SmsCode", "isp.QPS_LIMIT"));
    private static final Set<String> VERIFY_FAIL = Collections.singleton("isv.ValidateFail");
    private static final Set<String> PHONE_INVALID = new HashSet<>(Arrays.asList(
            "MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_NUMBER_ILLEGAL", "InvalidPhoneNumber.Check"));

    private AliyunSmsErrors() {
    }

    public static boolean isFrequency(@Nullable String code) {
        return code != null && (FREQUENCY.contains(code) || code.startsWith("Throttling"));
    }

    public static boolean isVerifyFail(@Nullable String code) {
        return code != null && VERIFY_FAIL.contains(code);
    }

    public static boolean isPhoneInvalid(@Nullable String code) {
        return code != null && PHONE_INVALID.contains(code);
    }

    /**
     * 阿里云返回码不是 OK 时的错误。
     *
     * @param cooldownSeconds 频率限制时的剩余间隔秒数，未知或不适用时传 0
     */
    @NonNull
    public static ApiException toApiException(@Nullable String providerCode, @Nullable String providerMessage,
                                              @Nullable String requestId, int cooldownSeconds) {
        JsonObject data = new JsonObject();
        if (providerCode != null) {
            data.addProperty(KEY_PROVIDER_CODE, providerCode);
        }
        if (isFrequency(providerCode)) {
            if (cooldownSeconds > 0) {
                data.addProperty(KEY_COOLDOWN, cooldownSeconds);
            }
            return new ApiException(ErrorCodes.TOO_FREQUENT, MSG_TOO_FREQUENT, requestId, data, null, null);
        }
        if (isVerifyFail(providerCode)) {
            return new ApiException(ErrorCodes.SMS_CODE_WRONG, MSG_CODE_WRONG, requestId, data, null, null);
        }
        if (isPhoneInvalid(providerCode)) {
            return new ApiException(ErrorCodes.PARAM_INVALID, MSG_PHONE_INVALID, requestId, data, null, null);
        }
        return new ApiException(ErrorCodes.SERVICE_UNAVAILABLE, failedMessage(providerCode, providerMessage),
                requestId, data, null, null);
    }

    /** CheckSmsVerifyCode 调用成功但 VerifyResult 不是 PASS */
    @NonNull
    public static ApiException verifyFailed(@Nullable String requestId) {
        return new ApiException(ErrorCodes.SMS_CODE_WRONG, MSG_CODE_WRONG, requestId, null, null, null);
    }

    /** 响应不是阿里云的 JSON（例如网关错误页） */
    @NonNull
    public static ApiException badResponse(int httpCode, @Nullable String requestId) {
        return new ApiException(ErrorCodes.SERVICE_UNAVAILABLE, MSG_FAILED_PREFIX + "响应无法解析（HTTP " + httpCode + "）",
                requestId, null, null, null);
    }

    @NonNull
    static String failedMessage(@Nullable String providerCode, @Nullable String providerMessage) {
        String msg = providerMessage == null || providerMessage.trim().isEmpty() ? "未知错误" : providerMessage.trim();
        return providerCode == null || providerCode.isEmpty()
                ? MSG_FAILED_PREFIX + msg
                : MSG_FAILED_PREFIX + msg + "（" + providerCode + "）";
    }
}
