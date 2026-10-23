package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.request.AccountSmsCodeRequest;
import com.zhimu.server.biz.account.request.SmsCodeSendRequest;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.CancellationRules;
import com.zhimu.server.biz.account.support.DateTimeText;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.support.SmsCodeStore;
import com.zhimu.server.biz.account.vo.SmsCodeSendVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.util.Masks;
import com.zhimu.server.integration.sms.SmsClient;
import com.zhimu.server.integration.sms.SmsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 验证码发送与校验：场景隔离、60 秒冷却、每天 10 条、连错 5 次作废。
 * 阿里云号码认证在内部限制发送频率与错误次数，业务侧仍需独立计数，保证 mock 与真实通道行为一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeServiceImpl implements SmsCodeService {

    private static final String MSG_COOLDOWN = "获取验证码太频繁，请稍后再试";
    private static final String MSG_LIMIT = "操作太频繁，请稍后再试";
    private static final String MSG_SEND_FAILED = "验证码发送失败，请稍后重试";
    private static final String MSG_CODE_WRONG = "验证码不正确，请重新输入";
    private static final String MSG_CODE_EXPIRED = "验证码已过期，请重新获取";

    private final SmsClient smsClient;
    private final SmsCodeStore smsCodeStore;
    private final UserMapper userMapper;
    private final AccountStateService accountStateService;
    private final CancellationRules cancellationRules;
    private final AccountProperties properties;

    @Override
    public SmsCodeSendVO send(SmsCodeSendRequest request) {
        Errors errors = new Errors();
        String phone = AccountValidation.phone(request == null ? null : request.phone());
        SmsScene scene = parseScene(request == null ? null : request.scene());
        if (!AccountValidation.isValidPhone(phone)) {
            errors.add("phone", phone.isEmpty() ? AccountValidation.MSG_PHONE_EMPTY : AccountValidation.MSG_PHONE_FORMAT);
        }
        if (scene == null) {
            errors.add("scene", "场景不正确");
        }
        errors.throwIfAny();

        String oldPhone = AccountValidation.phone(request.oldPhone());
        switch (scene) {
            case LOGIN, RESET_PASSWORD -> checkRegistered(phone);
            case REGISTER -> checkRegisterable(phone);
            case RECOVERY_NEW_PHONE -> {
                if (!AccountValidation.isValidPhone(oldPhone)) {
                    errors.add("old_phone", AccountValidation.MSG_PHONE_EMPTY);
                } else if (phone.equals(oldPhone)) {
                    errors.add("new_phone", AccountValidation.MSG_SAME_PHONE);
                } else if (existsPhone(phone)) {
                    throw new BizException(ErrorCode.PHONE_REGISTERED, AccountValidation.MSG_PHONE_TAKEN)
                            .with("reason", "PHONE_TAKEN");
                }
                errors.throwIfAny();
            }
            default -> {
            }
        }
        sendCode(phone, scene);
        return SmsCodeSendVO.builder()
                .phoneMasked(Masks.phone(phone))
                .cooldownSeconds(properties.getSmsCooldownSeconds())
                .expireSeconds(properties.getSmsExpireSeconds())
                .build();
    }

    @Override
    public SmsCodeSendVO sendToSelf(AccountSmsCodeRequest request) {
        User user = accountStateService.requireUser(UserContext.requireUserId());
        SmsScene scene = parseScene(request == null ? null : request.scene());
        if (scene != SmsScene.REGENERATE_RECOVERY_CODE && scene != SmsScene.CANCEL_ACCOUNT) {
            throw Errors.fail("scene", "场景不正确");
        }
        if (scene == SmsScene.CANCEL_ACCOUNT) {
            var check = cancellationRules.evaluate(user);
            if (!check.canCancel()) {
                throw cancellationRules.blockedException(check);
            }
        }
        if (user.getPhone() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号没有可用的手机号");
        }
        sendCode(user.getPhone(), scene);
        return SmsCodeSendVO.builder()
                .phoneMasked(Masks.phone(user.getPhone()))
                .cooldownSeconds(properties.getSmsCooldownSeconds())
                .expireSeconds(properties.getSmsExpireSeconds())
                .build();
    }

    @Override
    @Transactional
    public void verifyAndConsume(SmsScene scene, String phone, String code, String field) {
        if (!AccountValidation.isValidSmsCode(code)) {
            throw Errors.fail(field, AccountValidation.MSG_CODE_FORMAT);
        }
        if (smsCodeStore.isVerified(scene, phone)) {
            smsCodeStore.clear(scene, phone);
            return;
        }
        verifyInternal(scene, phone, code, field, true);
    }

    @Override
    public void verify(SmsScene scene, String phone, String code, String field) {
        verifyInternal(scene, phone, code, field, false);
    }

    private void verifyInternal(SmsScene scene, String phone, String code, String field, boolean consume) {
        if (!AccountValidation.isValidSmsCode(code)) {
            throw Errors.fail(field, AccountValidation.MSG_CODE_FORMAT);
        }
        if (!smsCodeStore.isSent(scene, phone)) {
            throw smsError(field, MSG_CODE_EXPIRED);
        }
        boolean pass;
        try {
            pass = smsClient.verify(phone, code);
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("校验短信验证码失败 scene={} phone={}", scene, Masks.phone(phone), e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_SEND_FAILED);
        }
        if (!pass) {
            int attempts = smsCodeStore.recordWrongAttempt(scene, phone, properties.getSmsExpireSeconds());
            if (attempts >= properties.getSmsMaxAttempts()) {
                smsCodeStore.clear(scene, phone);
                throw smsError(field, MSG_CODE_EXPIRED);
            }
            throw smsError(field, MSG_CODE_WRONG);
        }
        if (consume) {
            smsCodeStore.clear(scene, phone);
        } else {
            smsCodeStore.markVerified(scene, phone, properties.getSmsExpireSeconds());
        }
    }

    private static BizException smsError(String field, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("field_errors", List.of(new com.zhimu.server.common.error.FieldErrorVO(field, message)));
        return new BizException(ErrorCode.SMS_CODE_WRONG, message, data);
    }

    /** 发送：冷却 → 日上限 → 调通道 → 写冷却/已发/计数；失败回退计数 */
    private void sendCode(String phone, SmsScene scene) {
        long cooldown = smsCodeStore.cooldownSeconds(scene, phone);
        if (cooldown > 0) {
            throw new BizException(ErrorCode.TOO_FREQUENT, MSG_COOLDOWN).with("cooldown_seconds", cooldown);
        }
        if (smsCodeStore.dailyCount(phone) >= properties.getSmsDailyLimit()) {
            throw new BizException(ErrorCode.TOO_FREQUENT, MSG_LIMIT)
                    .with("cooldown_seconds", secondsToMidnight());
        }
        try {
            smsClient.send(phone, templateOf(scene));
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("发送短信失败 scene={} phone={}", scene, Masks.phone(phone), e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, MSG_SEND_FAILED);
        }
        smsCodeStore.startCooldown(scene, phone, properties.getSmsCooldownSeconds());
        smsCodeStore.markSent(scene, phone, properties.getSmsExpireSeconds());
        smsCodeStore.increaseDailyCount(phone);
    }

    private void checkRegistered(String phone) {
        User user = userMapper.selectByPhone(phone);
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new Errors().add("phone", AccountValidation.MSG_PHONE_NOT_REGISTERED)
                    .reason("PHONE_NOT_REGISTERED").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED,
                    "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。");
        }
    }

    private void checkRegisterable(String phone) {
        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            return;
        }
        if (UserStatus.CANCELLED.name().equals(user.getStatus())) {
            LocalDate release = user.getCancelledAt() == null ? null
                    : user.getCancelledAt().toLocalDate().plusDays(properties.getPhoneRelease().toDays());
            if (release != null && release.isAfter(BeijingTime.today())) {
                String message = "这个手机号注销未满 30 天，" + DateTimeText.monthDayCn(release) + "起可以重新注册";
                throw new BizException(ErrorCode.PHONE_REGISTERED, message).with("reason", "PHONE_CANCEL_COOLING");
            }
            return;
        }
        throw new BizException(ErrorCode.PHONE_REGISTERED, AccountValidation.MSG_PHONE_REGISTERED)
                .with("reason", "PHONE_REGISTERED");
    }

    private boolean existsPhone(String phone) {
        return userMapper.selectByPhone(phone) != null;
    }

    private long secondsToMidnight() {
        LocalDateTime next = LocalDate.now(BeijingTime.clock()).plusDays(1).atStartOfDay();
        return Math.max(1, Duration.between(BeijingTime.now(), next).toSeconds());
    }

    private static SmsTemplate templateOf(SmsScene scene) {
        return switch (scene) {
            case RESET_PASSWORD -> SmsTemplate.RESET_PASSWORD;
            case RECOVERY_NEW_PHONE -> SmsTemplate.BIND_NEW_PHONE;
            case REGENERATE_RECOVERY_CODE, CANCEL_ACCOUNT -> SmsTemplate.VERIFY_BOUND_PHONE;
            default -> SmsTemplate.LOGIN_REGISTER;
        };
    }

    static SmsScene parseScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return null;
        }
        try {
            return SmsScene.valueOf(scene.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
