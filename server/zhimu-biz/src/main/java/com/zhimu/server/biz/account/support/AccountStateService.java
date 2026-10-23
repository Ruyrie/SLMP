package com.zhimu.server.biz.account.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.entity.RecoveryCode;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.enums.NextStep;
import com.zhimu.server.biz.account.enums.RecoveryCodeRevokeReason;
import com.zhimu.server.biz.account.enums.RecoveryCodeScene;
import com.zhimu.server.biz.account.enums.RecoveryCodeStatus;
import com.zhimu.server.biz.account.enums.TokenRevokeReason;
import com.zhimu.server.biz.account.enums.UserStatus;
import com.zhimu.server.biz.account.mapper.RecoveryCodeMapper;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.vo.AuthTokenVO;
import com.zhimu.server.biz.account.vo.RecoveryCodeVO;
import com.zhimu.server.biz.account.vo.RecoveryInfoVO;
import com.zhimu.server.biz.account.vo.SessionVO;
import com.zhimu.server.biz.account.convert.RecoveryCodeConvert;
import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.farm.api.FarmAccountService;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.security.cache.AuthStateCache;
import com.zhimu.server.framework.security.token.TokenPair;
import com.zhimu.server.framework.security.token.UserTokenService;
import com.zhimu.server.framework.tx.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号状态与令牌的公共处理：恢复码生成/作废、强制步骤判定、令牌签发与失效、找回信息组装。
 * 所有响应中的恢复码明文只经过本类返回给调用方，不缓存、不写日志。
 */
@Service
@RequiredArgsConstructor
public class AccountStateService {

    private final UserMapper userMapper;
    private final RecoveryCodeMapper recoveryCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountProperties properties;
    private final UserTokenService userTokenService;
    private final AuthStateCache authStateCache;
    private final FarmAccountService farmAccountService;

    public User findByPhone(String phone) {
        return phone == null ? null : userMapper.selectByPhone(phone);
    }

    public User findById(Long userId) {
        return userId == null ? null : userMapper.selectById(userId);
    }

    /** 当前登录用户；账号不存在按 40104 EXPIRED */
    public User requireUser(Long userId) {
        User user = findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        return user;
    }

    public RecoveryCode liveCode(Long userId) {
        return recoveryCodeMapper.selectOne(Wrappers.<RecoveryCode>lambdaQuery()
                .eq(RecoveryCode::getUserId, userId)
                .isNotNull(RecoveryCode::getLiveFlag)
                .orderByDesc(RecoveryCode::getId)
                .last("LIMIT 1"));
    }

    public RecoveryCode activeCode(Long userId) {
        return recoveryCodeMapper.selectOne(Wrappers.<RecoveryCode>lambdaQuery()
                .eq(RecoveryCode::getUserId, userId)
                .eq(RecoveryCode::getStatus, RecoveryCodeStatus.ACTIVE.name())
                .isNotNull(RecoveryCode::getLiveFlag)
                .orderByDesc(RecoveryCode::getId)
                .last("LIMIT 1"));
    }

    /**
     * 强制步骤判定（模块01 文档 2.7.4，按 App 兼容修正）：
     * 必须改密 → RECOVERY_CHANGE_PASSWORD；有待确认码且场景为 REGISTER / RECOVERY → SAVE_RECOVERY_CODE；
     * REGENERATE 只约束生成时的客户端；没有任何未作废的码时不再强制（App 员工示例 next_step=NONE，
     * 用户可在账号安全页主动生成）。
     */
    public NextStep resolveNextStep(User user, ClientType client) {
        if (user == null) {
            return NextStep.NONE;
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            return NextStep.RECOVERY_CHANGE_PASSWORD;
        }
        return resolveNextStep(user.getId(), client);
    }

    public NextStep resolveNextStep(Long userId, ClientType client) {
        RecoveryCode live = liveCode(userId);
        if (live == null) {
            return NextStep.NONE;
        }
        if (!RecoveryCodeStatus.PENDING_CONFIRM.name().equals(live.getStatus())) {
            return NextStep.NONE;
        }
        RecoveryCodeScene scene = parseScene(live.getScene());
        if (scene == RecoveryCodeScene.REGISTER || scene == RecoveryCodeScene.RECOVERY) {
            return NextStep.SAVE_RECOVERY_CODE;
        }
        if (scene == RecoveryCodeScene.REGENERATE && sameClient(live.getClientType(), client)) {
            return NextStep.SAVE_RECOVERY_CODE;
        }
        return NextStep.NONE;
    }

    /** 生成新的待确认恢复码：同一事务先作废旧码，明文只放在返回值中。 */
    @Transactional
    public RecoveryCodeVO generateRecoveryCode(User user, RecoveryCodeScene scene, boolean regenerated,
                                               ClientType client, RecoveryCodeRevokeReason revokeReason) {
        RecoveryCode old = liveCode(user.getId());
        if (old != null) {
            doRevoke(old, revokeReason);
        }
        String plaintext = Secrets.recoveryCode();
        LocalDateTime now = BeijingTime.now();
        RecoveryCode code = new RecoveryCode();
        code.setUserId(user.getId());
        code.setCodeHash(passwordEncoder.encode(plaintext));
        code.setStatus(RecoveryCodeStatus.PENDING_CONFIRM.name());
        code.setScene(scene.name());
        code.setRegenerated(regenerated);
        code.setClientType(client == null ? ClientType.APP_ANDROID.name() : client.name());
        code.setLiveFlag(1);
        code.setCreatedAt(now);
        code.setUpdatedAt(now);
        recoveryCodeMapper.insert(code);
        return RecoveryCodeConvert.toVO(code, plaintext, user.getPhone());
    }

    /** 作废当前未作废的恢复码 */
    @Transactional
    public void revokeLiveCode(Long userId, RecoveryCodeRevokeReason reason) {
        RecoveryCode live = liveCode(userId);
        if (live != null) {
            doRevoke(live, reason);
        }
    }

    @Transactional
    public void revokeAllCodes(Long userId, RecoveryCodeRevokeReason reason) {
        List<RecoveryCode> codes = recoveryCodeMapper.selectList(Wrappers.<RecoveryCode>lambdaQuery()
                .eq(RecoveryCode::getUserId, userId)
                .isNotNull(RecoveryCode::getLiveFlag));
        for (RecoveryCode code : codes) {
            doRevoke(code, reason);
        }
    }

    private void doRevoke(RecoveryCode code, RecoveryCodeRevokeReason reason) {
        LocalDateTime now = BeijingTime.now();
        code.setStatus(RecoveryCodeStatus.REVOKED.name());
        code.setLiveFlag(null);
        code.setRevokedAt(now);
        code.setRevokeReason(reason.name());
        code.setUpdatedAt(now);
        recoveryCodeMapper.updateById(code);
    }

    /** 修改姓名：profile_version 乐观锁（不复用 version），成功返回 1 */
    @Transactional
    public int updateProfile(Long userId, String name, int expectedVersion, LocalDateTime now) {
        return userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .eq(User::getProfileVersion, expectedVersion)
                .set(User::getName, name)
                .setSql("profile_version = profile_version + 1")
                .set(User::getUpdatedAt, now));
    }

    /** token_version 加 1；提交后作废刷新令牌并删除 auth:state 缓存 */
    @Transactional
    public void invalidateTokens(User user, TokenRevokeReason reason) {
        LocalDateTime now = BeijingTime.now();
        userMapper.bumpTokenVersion(user.getId(), reason.name(), now);
        int next = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(next);
        user.setTokenRevokeReason(reason.name());
        user.setTokenRevokedAt(now);
        Long userId = user.getId();
        AfterCommit.run(() -> {
            userTokenService.revokeAll(userId);
            authStateCache.evict(userId);
        });
    }

    public TokenPair issue(User user, ClientType client, String deviceId) {
        return userTokenService.issue(user.getId(), client, deviceId,
                user.getTokenVersion() == null ? 0 : user.getTokenVersion());
    }

    /** 登录/刷新/改密后统一组装 AuthTokenVO */
    public AuthTokenVO toTokenVO(User user, TokenPair pair, ClientType client, RecoveryCodeVO recoveryCode) {
        NextStep step = resolveNextStep(user, client);
        Long lastFarmId = defaultFarm(user);
        RecoveryInfoVO info = step == NextStep.RECOVERY_CHANGE_PASSWORD
                ? UserConvert.toRecoveryInfo(user, properties) : null;
        return AuthTokenVO.builder()
                .accessToken(pair.accessToken())
                .tokenType(pair.tokenType() == null ? "Bearer" : pair.tokenType())
                .expiresIn((int) pair.expiresIn())
                .refreshExpiresIn((int) pair.refreshExpiresIn())
                .refreshToken(pair.refreshToken())
                .nextStep(step.name())
                .nextStepText(step.getText())
                .user(UserConvert.toAccountUser(user))
                .lastFarmId(lastFarmId)
                .recoveryInfo(info)
                .recoveryCode(recoveryCode)
                .build();
    }

    public SessionVO toSessionVO(User user, ClientType client) {
        NextStep step = resolveNextStep(user, client);
        return SessionVO.builder()
                .user(UserConvert.toAccountUser(user))
                .nextStep(step.name())
                .nextStepText(step.getText())
                .recoveryInfo(step == NextStep.RECOVERY_CHANGE_PASSWORD
                        ? UserConvert.toRecoveryInfo(user, properties) : null)
                .lastFarmId(defaultFarm(user))
                .build();
    }

    /** A08/A15 的 last_farm_id：排除冻结/删除后的默认养殖场 */
    public Long defaultFarm(User user) {
        if (user == null) {
            return null;
        }
        try {
            return farmAccountService.resolveDefaultFarm(user.getId(), user.getLastFarmId());
        } catch (RuntimeException e) {
            // 默认养殖场计算失败不应阻断登录与会话恢复
            return null;
        }
    }

    public RecoveryInfoVO recoveryInfo(User user) {
        return UserConvert.toRecoveryInfo(user, properties);
    }

    public AccountProperties properties() {
        return properties;
    }

    public static RecoveryCodeScene parseScene(String scene) {
        if (scene == null) {
            return null;
        }
        try {
            return RecoveryCodeScene.valueOf(scene);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean sameClient(String codeClient, ClientType requestClient) {
        if (requestClient == null) {
            return false;
        }
        return requestClient.name().equals(codeClient);
    }

    /** 账号是否可登录（未注销、未停用） */
    public static void checkLoginable(User user, String field) {
        if (user == null || UserStatus.CANCELLED.name().equals(user.getStatus())) {
            throw new Errors().add(field, AccountValidation.MSG_PHONE_NOT_REGISTERED)
                    .reason("PHONE_NOT_REGISTERED").toException();
        }
        if (UserStatus.DISABLED.name().equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED,
                    "这个账号已被平台停用。如有疑问，请拨打客服电话 400-123-4567。");
        }
    }
}
