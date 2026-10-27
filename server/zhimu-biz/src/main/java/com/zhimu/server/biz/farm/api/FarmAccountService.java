package com.zhimu.server.biz.farm.api;

import com.zhimu.server.common.vo.FarmBriefVO;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 模块02 提供给模块01 的账号相关能力（模块02 文档 1.4 节 FarmAccountService，规则见其 2.7 节）。
 */
public interface FarmAccountService {

    /**
     * 登录与会话恢复时的默认养殖场：lastFarmId 仍为在场、未删除、未冻结的养殖场时用它；
     * 否则按 F01 排序取第一个未冻结的养殖场；都没有返回 null。
     */
    Long resolveDefaultFarm(Long userId, Long lastFarmId);

    /** farmId 是本人有效养殖场时返回简要信息（API-A15 的 current_farm），否则返回 null */
    FarmBriefVO findMyFarmBrief(Long userId, Long farmId);

    /** API-A15 的 farm_stats */
    AccountFarmStats farmStats(Long userId);

    /** 注销预览：本人为所有者的养殖场（未删除） */
    List<CancelOwnedFarm> listOwnedFarmsForCancel(Long userId);

    /** 注销预览：本人为场长或员工、注销时将退出的养殖场 */
    List<CancelExitFarm> listExitFarmsForCancel(Long userId);

    /**
     * 注销执行（在模块01 的注销事务内同步调用）：本人创建的养殖场按处置方式删除或标记，退出其他养殖场，
     * 撤回相关邀请与转让，删除成员缓存，并在事务提交后通知其他成员（模块02 文档“账号注销联动”）。
     * 调用前模块01 已校验没有 TRANSFER_OR_DISABLE、WAIT_UNFREEZE 的养殖场。
     */
    void handleAccountCancelled(Long userId, String phone);

    /**
     * 账号清理事务内调用。逐场按停用时间+保留期清理仍属于该注销所有者的场；
     * 返回最早尚未到期的时间，没有剩余返回 null。不会按注销清单的最早日期批量提前删除。
     */
    LocalDateTime cleanupCancelledOwnerFarms(Long userId, LocalDateTime now);
}
