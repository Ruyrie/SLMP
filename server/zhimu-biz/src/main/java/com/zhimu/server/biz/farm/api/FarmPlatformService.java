package com.zhimu.server.biz.farm.api;

import java.util.List;

/** 管理端冻结、解冻（ADM-S17/S18）与接收人名单。只提供 Service，不提供 Controller。 */
public interface FarmPlatformService {

    void freeze(Long farmId, Long adminId, String reason, int version);

    void unfreeze(Long farmId, Long adminId, int version);

    /** 养殖场全部在场成员的 user_id（消息、推送接收人） */
    List<Long> listActiveUserIds(Long farmId);
}
