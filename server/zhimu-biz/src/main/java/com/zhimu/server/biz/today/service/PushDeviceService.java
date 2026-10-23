package com.zhimu.server.biz.today.service;

import com.zhimu.server.biz.today.request.PushDeviceRequest;
import com.zhimu.server.biz.today.vo.PushDeviceVO;

/** 推送设备登记与解绑（API-T20、T21）。 */
public interface PushDeviceService {

    PushDeviceVO register(PushDeviceRequest request, Long userId);

    void unbind(String registrationId, Long userId);
}
