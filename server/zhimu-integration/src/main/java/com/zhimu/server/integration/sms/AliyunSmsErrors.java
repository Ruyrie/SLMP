package com.zhimu.server.integration.sms;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.error.FieldErrorVO;

import java.util.List;
import java.util.Set;

/**
 * 阿里云返回码 → 业务错误（与 App 的 AliyunSmsErrors 分类一致）：
 * <ul>
 * <li>频率与日上限 → 42901，带 data.cooldown_seconds；</li>
 * <li>验证码错误或过期（isv.ValidateFail）→ 校验返回 false（由 {@link AliyunSmsClient} 处理）；</li>
 * <li>手机号格式错误 → 40001（field_errors 中 field 为 phone）；</li>
 * <li>其他（签名错误、Key 无效、未开通、参数错误等）→ 50301，阿里云返回码与 RequestId 只写日志。</li>
 * </ul>
 */
public final class AliyunSmsErrors {

    public static final String MSG_TOO_FREQUENT = "操作太频繁，请稍后再试";
    public static final String MSG_PHONE_INVALID = "手机号格式不正确，请输入 11 位手机号";
    public static final String MSG_FAILED = "短信服务暂时不可用，请稍后重试";
    public static final String KEY_COOLDOWN = "cooldown_seconds";

    private static final Set<String> FREQUENCY = Set.of(
            "biz.FREQUENCY", "FREQUENCY_FAIL", "isv.BUSINESS_LIMIT_CONTROL", "BUSINESS_LIMIT_CONTROL",
            "isv.DAY_LIMIT_CONTROL", "LimitExceeded.SmsCode", "isp.QPS_LIMIT");
    private static final Set<String> VERIFY_FAIL = Set.of("isv.ValidateFail");
    private static final Set<String> PHONE_INVALID = Set.of(
            "MOBILE_NUMBER_ILLEGAL", "isv.MOBILE_NUMBER_ILLEGAL", "InvalidPhoneNumber.Check");

    private AliyunSmsErrors() {
    }

    public static boolean isFrequency(String code) {
        return code != null && (FREQUENCY.contains(code) || code.startsWith("Throttling"));
    }

    public static boolean isVerifyFail(String code) {
        return code != null && VERIFY_FAIL.contains(code);
    }

    public static boolean isPhoneInvalid(String code) {
        return code != null && PHONE_INVALID.contains(code);
    }

    /**
     * 返回码不是 OK 时的异常（校验不通过不走这里）。
     *
     * @param cooldownSeconds 频率限制时的剩余间隔秒数
     */
    public static BizException toBizException(String providerCode, int cooldownSeconds) {
        if (isFrequency(providerCode)) {
            return new BizException(ErrorCode.TOO_FREQUENT, MSG_TOO_FREQUENT).with(KEY_COOLDOWN, cooldownSeconds);
        }
        if (isPhoneInvalid(providerCode)) {
            return new BizException(ErrorCode.PARAM_INVALID, MSG_PHONE_INVALID)
                    .with("field_errors", List.of(new FieldErrorVO("phone", MSG_PHONE_INVALID)));
        }
        return failed();
    }

    public static BizException failed() {
        return new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_FAILED);
    }
}
