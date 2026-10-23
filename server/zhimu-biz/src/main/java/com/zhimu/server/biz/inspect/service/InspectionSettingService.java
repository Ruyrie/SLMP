package com.zhimu.server.biz.inspect.service;

import com.zhimu.server.biz.inspect.request.InspectionSettingRequest;
import com.zhimu.server.biz.inspect.vo.InspectionSettingVO;

/** API-I04 / I05 巡检截止时间与提醒规则。 */
public interface InspectionSettingService {

    InspectionSettingVO current();

    InspectionSettingVO update(InspectionSettingRequest request);
}
