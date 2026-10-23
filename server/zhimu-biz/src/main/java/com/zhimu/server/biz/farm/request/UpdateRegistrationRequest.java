package com.zhimu.server.biz.farm.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 修改注册信息 / 重新提交（API-F07）：CreateFarmRequest 全部字段 + audit_version（取自 F06） */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRegistrationRequest extends CreateFarmRequest {
    private Integer auditVersion;
}
