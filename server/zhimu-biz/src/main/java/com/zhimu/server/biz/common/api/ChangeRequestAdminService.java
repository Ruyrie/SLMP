package com.zhimu.server.biz.common.api;

/**
 * 管理端修改申请审核（管理端-01 ADM-R12、ADM-R13；第 1 批只出 Service，不写 /admin/v1 Controller）。
 * 所有方法校验 version（不一致 40902）、状态（不是 PENDING 40903），写 t_operation_log，给提交人发结果消息（不发送短信）。
 */
public interface ChangeRequestAdminService {

    /** 通过修改申请并让来源模块回写、重算；同一事务 */
    void approve(Long id, Long adminId, String opinion, int version);

    /** 驳回修改申请 */
    void reject(Long id, Long adminId, String reason, int version);

    /** 管理端菜单角标：待审核修改申请数 */
    long countPending();
}
