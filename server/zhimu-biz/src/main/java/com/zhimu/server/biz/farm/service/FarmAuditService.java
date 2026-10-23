package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.request.CreateFarmRequest;
import com.zhimu.server.biz.farm.request.UpdateRegistrationRequest;
import com.zhimu.server.biz.farm.vo.FarmAuditVO;

/** 新建提交、修改注册信息 / 重新提交、审核结果（API-F05–F07） */
public interface FarmAuditService {

    FarmAuditVO create(Long userId, CreateFarmRequest request);

    FarmAuditVO getAudit(Long farmId, Long userId);

    FarmAuditVO resubmit(Long farmId, Long userId, UpdateRegistrationRequest request);
}
