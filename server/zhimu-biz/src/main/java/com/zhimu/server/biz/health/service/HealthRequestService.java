package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.vo.HealthRequestStatusVO;

/** API-D41 按请求编号查询登记结果。 */
public interface HealthRequestService {

    HealthRequestStatusVO findByRequest(String clientRequestId, String recordType);
}
