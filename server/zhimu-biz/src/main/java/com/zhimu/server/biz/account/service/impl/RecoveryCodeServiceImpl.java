package com.zhimu.server.biz.account.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.enums.RecoveryCodeStatus;
import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.mapper.RecoveryCodeMapper;
import com.zhimu.server.biz.account.request.RecoveryCodeRegenerateRequest;
import com.zhimu.server.biz.account.service.RecoveryCodeService;
import com.zhimu.server.biz.account.service.SmsCodeService;
import com.zhimu.server.biz.account.support.AccountStateService;
import com.zhimu.server.biz.account.support.AccountValidation;
import com.zhimu.server.biz.account.support.Errors;
import com.zhimu.server.biz.account.convert.RecoveryCodeConvert;
import com.zhimu.server.biz.account.vo.RecoveryCodeConfirmVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 重新生成恢复码（强制步骤与短信验证两种入口）与确认已保存。 */
@Service
@RequiredArgsConstructor
public class RecoveryCodeServiceImpl implements RecoveryCodeService {

    private final AccountStateService accountState;
    private final RecoveryCodeMapper recoveryCodeMapper;
    private final SmsCodeService smsCodeService;

    @Override
    @Transactional
    public RecoveryCodeVO regeneratePending(ClientType client) {
        User user = accountState.requireUser(UserContext.requireUserId());
        NextStep step = accountState.resolveNextStep(user, client);
        if (step != NextStep.SAVE_RECOVERY_CODE) {
            throw new BizException(ErrorCode.FORCED_STEP_REQUIRED).with("next_step", step.name());
        }
        RecoveryCode live = accountState.liveCode(user.getId());
        RecoveryCodeScene scene;
        if (live != null) {
            scene = AccountStateService.parseScene(live.getScene());
        } else {
            RecoveryCode lastRevoked = recoveryCodeMapper.selectOne(Wrappers.<RecoveryCode>lambdaQuery()
                    .eq(RecoveryCode::getUserId, user.getId())
                    .eq(RecoveryCode::getStatus, RecoveryCodeStatus.REVOKED.name())
                    .orderByDesc(RecoveryCode::getId)
                    .last("LIMIT 1"));
            scene = lastRevoked != null
                    && RecoveryCodeRevokeReason.ACCOUNT_RECOVERED.name().equals(lastRevoked.getRevokeReason())
                    ? RecoveryCodeScene.RECOVERY : RecoveryCodeScene.REGISTER;
        }
        if (scene == null) {
            scene = RecoveryCodeScene.REGISTER;
        }
        return accountState.generateRecoveryCode(user, scene, true, client, RecoveryCodeRevokeReason.UNCONFIRMED);
    }

    @Override
    @Transactional
    public RecoveryCodeVO regenerateBySms(RecoveryCodeRegenerateRequest request, ClientType client) {
        User user = accountState.requireUser(UserContext.requireUserId());
        String smsCode = AccountValidation.trim(request == null ? null : request.smsCode());
        if (!AccountValidation.isValidSmsCode(smsCode)) {
            throw Errors.fail("sms_code", AccountValidation.MSG_CODE_FORMAT);
        }
        smsCodeService.verifyAndConsume(SmsScene.REGENERATE_RECOVERY_CODE, user.getPhone(), smsCode, "sms_code");
        accountState.revokeLiveCode(user.getId(), RecoveryCodeRevokeReason.REGENERATED);
        return accountState.generateRecoveryCode(user, RecoveryCodeScene.REGENERATE, false, client,
                RecoveryCodeRevokeReason.REGENERATED);
    }

    @Override
    @Transactional
    public RecoveryCodeConfirmVO confirm(Long recoveryCodeId) {
        User user = accountState.requireUser(UserContext.requireUserId());
        RecoveryCode code = recoveryCodeId == null ? null : recoveryCodeMapper.selectById(recoveryCodeId);
        if (code == null || !user.getId().equals(code.getUserId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (RecoveryCodeStatus.ACTIVE.name().equals(code.getStatus())) {
            return RecoveryCodeConvert.toConfirm(code, accountState.resolveNextStep(user, clientOf()).name());
        }
        RecoveryCode live = accountState.liveCode(user.getId());
        if (!RecoveryCodeStatus.PENDING_CONFIRM.name().equals(code.getStatus())
                || code.getLiveFlag() == null
                || live == null || !live.getId().equals(code.getId())) {
            throw new BizException(ErrorCode.STATE_CHANGED, "恢复码已更新，请保存最新的恢复码");
        }
        code.setStatus(RecoveryCodeStatus.ACTIVE.name());
        code.setConfirmedAt(BeijingTime.now());
        code.setUpdatedAt(BeijingTime.now());
        recoveryCodeMapper.updateById(code);
        return RecoveryCodeConvert.toConfirm(code, accountState.resolveNextStep(user, clientOf()).name());
    }

    private static ClientType clientOf() {
        return UserContext.get() == null ? ClientType.APP_ANDROID : UserContext.get().clientType();
    }
}
