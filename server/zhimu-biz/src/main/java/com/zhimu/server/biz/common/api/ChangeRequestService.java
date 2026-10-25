package com.zhimu.server.biz.common.api;

/**
 * 修改申请对其他模块提供的能力（公共能力 biz.common 实现）。
 */
public interface ChangeRequestService {

    /** 本人提交的待审核修改申请数；farmId 为 null 时统计全部养殖场（API-A15 的 change_request_pending_count） */
    long countPendingSubmittedBy(Long userId, Long farmId);

    /** 某批次的待审核修改申请数（任何提交人；批次档案管理视图用），默认 0 */
    default long countPendingByBatch(Long farmId, Long batchId) {
        return 0;
    }

    /** 提交人注销时由系统撤销其待审核申请（status = CANCELED，cancel_reason 为原因） */
    int cancelPendingBySubmitter(Long userId, String cancelReason);

    /** 养殖场删除时由系统撤销该场的待审核申请 */
    int cancelPendingByFarm(Long farmId, String cancelReason);
}