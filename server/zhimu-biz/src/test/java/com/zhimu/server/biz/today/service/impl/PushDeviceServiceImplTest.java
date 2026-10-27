package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.common.error.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import com.zhimu.server.biz.today.convert.PushDeviceConvert;
import com.zhimu.server.biz.today.entity.PushDevice;
import com.zhimu.server.biz.today.mapper.PushDeviceMapper;
import com.zhimu.server.biz.today.request.PushDeviceRequest;
import com.zhimu.server.biz.today.vo.PushDeviceVO;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.integration.push.PushResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

/** API-T20/T21：别名绑定、同设备换账号先解绑、解绑幂等。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushDeviceServiceImplTest {

    @Mock private PushDeviceMapper pushDeviceMapper;
    @Mock private PushClient pushClient;
    @Mock private UserMapper userMapper;

    private PushDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant metadata = new MapperBuilderAssistant(new MybatisConfiguration(), "push-device-test");
        metadata.setCurrentNamespace(PushDeviceMapper.class.getName());
        TableInfoHelper.initTableInfo(metadata, PushDevice.class);
        service = new PushDeviceServiceImpl(pushDeviceMapper, new PushDeviceConvert(), pushClient, userMapper);
        User user = new User();
        user.setId(10001L);
        user.setStatus("NORMAL");
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user);
        when(pushClient.bindAlias(anyString(), anyString())).thenReturn(PushResult.sent("msg-1"));
        when(pushClient.unbindAlias(anyString())).thenReturn(PushResult.sent(null));
        when(pushDeviceMapper.insert(any(PushDevice.class))).thenReturn(1);
        when(pushDeviceMapper.updateById(any(PushDevice.class))).thenReturn(1);
    }

    @Test
    void registerBindsAliasAndActivates() {
        when(pushDeviceMapper.selectOne(any())).thenReturn(null);

        PushDeviceVO vo = service.register(request("190e35f7e0a5b1c2d3"), 10001L);

        assertThat(vo.getRegistrationId()).isEqualTo("190e35f7e0a5b1c2d3");
        assertThat(vo.getJpushAlias()).isEqualTo("u10001");
        assertThat(vo.getStatus()).isEqualTo("ACTIVE");
        assertThat(vo.getAliasBound()).isTrue();
        verify(pushClient).bindAlias("190e35f7e0a5b1c2d3", "u10001");
        verify(pushDeviceMapper).insert(any(PushDevice.class));
    }

    @Test
    void registerSameDeviceOtherUserUnbindsOldAliasFirst() {
        PushDevice old = device(10002L, "u10002", true, "ACTIVE");
        when(pushDeviceMapper.selectOne(any())).thenReturn(old);

        service.register(request("190e35f7e0a5b1c2d3"), 10001L);

        verify(pushClient).unbindAlias("190e35f7e0a5b1c2d3");
        verify(pushClient).bindAlias("190e35f7e0a5b1c2d3", "u10001");
        assertThat(old.getUserId()).isEqualTo(10001L);
        assertThat(old.getStatus()).isEqualTo("ACTIVE");
        var order = inOrder(userMapper, pushDeviceMapper, pushClient);
        order.verify(userMapper).selectByIdForUpdate(10001L);
        order.verify(pushDeviceMapper).selectOne(any());
        order.verify(pushClient).unbindAlias("190e35f7e0a5b1c2d3");
        order.verify(pushClient).bindAlias("190e35f7e0a5b1c2d3", "u10001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<PushDevice>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(pushDeviceMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).endsWith("LIMIT 1 FOR UPDATE");
    }

    @Test
    void unbindNotOwnedDeviceDoesNothing() {
        when(pushDeviceMapper.selectOne(any())).thenReturn(null);

        service.unbind("190e35f7e0a5b1c2d3", 10001L);

        verify(pushClient, never()).unbindAlias(anyString());
        verify(pushDeviceMapper, never()).updateById(any(PushDevice.class));
    }

    @Test
    void unbindOwnedDeviceMarksUnbound() {
        PushDevice device = device(10001L, "u10001", true, "ACTIVE");
        when(pushDeviceMapper.selectOne(any())).thenReturn(device);

        service.unbind("190e35f7e0a5b1c2d3", 10001L);

        assertThat(device.getStatus()).isEqualTo("UNBOUND");
        assertThat(device.getAliasBound()).isFalse();
        verify(pushClient).unbindAlias("190e35f7e0a5b1c2d3");
    }

    @Test
    void cancelledAccountCannotRebindOrUnbindAnyDevice() {
        User user = new User();
        user.setId(10001L);
        user.setStatus("CANCELLED");
        when(userMapper.selectByIdForUpdate(10001L)).thenReturn(user);

        assertThatThrownBy(() -> service.register(request("isolated-device"), 10001L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40104);
        assertThatThrownBy(() -> service.unbind("isolated-device", 10001L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40104);
        verifyNoInteractions(pushDeviceMapper, pushClient);
    }

    private static PushDeviceRequest request(String registrationId) {
        PushDeviceRequest request = new PushDeviceRequest();
        request.setRegistrationId(registrationId);
        request.setPlatform("ANDROID");
        request.setDeviceBrand("Xiaomi");
        request.setDeviceModel("Xiaomi 14");
        request.setOsVersion("Android 15");
        request.setAppVersion("1.0.0");
        request.setNotifyPermission(true);
        return request;
    }

    private static PushDevice device(long userId, String alias, boolean bound, String status) {
        PushDevice device = new PushDevice();
        device.setId(1L);
        device.setUserId(userId);
        device.setPlatform("ANDROID");
        device.setRegistrationId("190e35f7e0a5b1c2d3");
        device.setJpushAlias(alias);
        device.setAliasBound(bound);
        device.setStatus(status);
        device.setNotifyPermission(true);
        return device;
    }
}
