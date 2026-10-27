package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.today.convert.PushDeviceConvert;
import com.zhimu.server.biz.today.entity.PushDevice;
import com.zhimu.server.biz.today.mapper.PushDeviceMapper;
import com.zhimu.server.biz.today.request.PushDeviceRequest;
import com.zhimu.server.biz.today.service.PushDeviceService;
import com.zhimu.server.biz.today.vo.PushDeviceVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.integration.push.PushClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-T20/T21 推送设备：同一 registration_id 原属他人时先解绑原用户；写 ACTIVE、last_active_at；
 * 别名 u{用户 ID} 未绑定或换用户时调用极光设备接口，失败只记 alias_bound = 0，不影响接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeviceServiceImpl implements PushDeviceService {

    private final PushDeviceMapper pushDeviceMapper;
    private final PushDeviceConvert convert;
    private final PushClient pushClient;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public PushDeviceVO register(PushDeviceRequest request, Long userId) {
        if (!"ANDROID".equalsIgnoreCase(request.getPlatform())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前客户端不支持推送设备登记");
        }
        requireActiveUserForUpdate(userId);
        String alias = PushClient.alias(userId);
        PushDevice device = pushDeviceMapper.selectOne(Wrappers.<PushDevice>lambdaQuery()
                .eq(PushDevice::getRegistrationId, request.getRegistrationId())
                .last("LIMIT 1 FOR UPDATE"));
        boolean changedUser = device != null && !userId.equals(device.getUserId());
        if (changedUser) {
            // 同一设备换账号：先解除原用户别名，避免旧账号继续收到推送
            pushClient.unbindAlias(request.getRegistrationId());
        }
        if (device == null) {
            device = new PushDevice();
            device.setRegistrationId(request.getRegistrationId());
            device.setPlatform("ANDROID");
            device.setLastLoginAt(BeijingTime.now());
        }
        boolean wasBound = Boolean.TRUE.equals(device.getAliasBound());
        String oldAlias = device.getJpushAlias();
        device.setUserId(userId);
        device.setJpushAlias(alias);
        device.setDeviceBrand(request.getDeviceBrand());
        device.setDeviceModel(request.getDeviceModel());
        device.setOsVersion(request.getOsVersion());
        device.setAppVersion(request.getAppVersion());
        device.setNotifyPermission(Boolean.TRUE.equals(request.getNotifyPermission()));
        device.setStatus("ACTIVE");
        device.setLastActiveAt(BeijingTime.now());

        boolean needBind = changedUser || !wasBound || !alias.equals(oldAlias);
        boolean bound = wasBound && !changedUser;
        if (needBind) {
            bound = pushClient.bindAlias(request.getRegistrationId(), alias).isSent();
            if (!bound) {
                log.info("极光别名未绑定成功 registration_id={} alias={}", request.getRegistrationId(), alias);
            }
        }
        device.setAliasBound(bound);

        if (device.getId() == null) {
            pushDeviceMapper.insert(device);
        } else {
            pushDeviceMapper.updateById(device);
        }
        return convert.toVO(device);
    }

    @Override
    @Transactional
    public void unbind(String registrationId, Long userId) {
        if (registrationId == null || registrationId.isBlank()) {
            return;
        }
        requireActiveUserForUpdate(userId);
        PushDevice device = pushDeviceMapper.selectOne(Wrappers.<PushDevice>lambdaQuery()
                .eq(PushDevice::getRegistrationId, registrationId)
                .eq(PushDevice::getUserId, userId)
                .last("LIMIT 1 FOR UPDATE"));
        if (device == null) {
            // 不属于本人或不存在时直接成功（幂等）
            return;
        }
        pushClient.unbindAlias(registrationId);
        device.setStatus("UNBOUND");
        device.setAliasBound(false);
        pushDeviceMapper.updateById(device);
    }

    /** 与账号注销/清理共用 user → device 锁序，鉴权通过后账号也可能已被另一个请求注销。 */
    private void requireActiveUserForUpdate(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || !"NORMAL".equals(user.getStatus())) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason",
                    user != null && "CANCELLED".equals(user.getStatus()) ? "ACCOUNT_CANCELLED" : "EXPIRED");
        }
    }
}
