package com.zhimu.server.biz.farm.api;

import java.util.List;

/** 管理端注册审核（ADM-R04/R05）：通过、驳回、待审核数。只提供 Service，不提供 Controller。 */
public interface FarmAuditAdminService {

    void approve(Long auditId, Long adminId, String opinion, int version);

    void reject(Long auditId, Long adminId, String reason, List<Long> reshootFileIds, int version);

    long countPending();
}
