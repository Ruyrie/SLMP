package com.zhimu.server.biz.farm.api;

/** 管理端资料变更审核（ADM-R08/R09）：通过、驳回、待审核数。只提供 Service，不提供 Controller。 */
public interface FarmChangeAdminService {

    void approve(Long changeId, Long adminId, String opinion, int version);

    void reject(Long changeId, Long adminId, String reason, int version);

    long countPending();
}
