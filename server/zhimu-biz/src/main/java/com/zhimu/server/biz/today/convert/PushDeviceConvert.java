package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.today.entity.PushDevice;
import com.zhimu.server.biz.today.vo.PushDeviceVO;
import org.springframework.stereotype.Component;

/** 推送设备 Entity → VO（API-T20）。 */
@Component
public class PushDeviceConvert {

    public PushDeviceVO toVO(PushDevice device) {
        PushDeviceVO vo = new PushDeviceVO();
        vo.setRegistrationId(device.getRegistrationId());
        vo.setJpushAlias(device.getJpushAlias());
        vo.setStatus(device.getStatus());
        vo.setAliasBound(Boolean.TRUE.equals(device.getAliasBound()));
        vo.setNotifyPermission(Boolean.TRUE.equals(device.getNotifyPermission()));
        return vo;
    }
}
