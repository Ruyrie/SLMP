package com.zhimu.slmp.farm.model.request;

/**
 * 修改注册信息 / 重新提交（API-F07，要求 Idempotency-Key）：CreateFarmRequest 全部字段 + audit_version（取自 API-F06）。
 */
public class UpdateRegistrationRequest extends CreateFarmRequest {

    private int auditVersion;

    public int getAuditVersion() {
        return auditVersion;
    }

    public UpdateRegistrationRequest setAuditVersion(int auditVersion) {
        this.auditVersion = auditVersion;
        return this;
    }
}
