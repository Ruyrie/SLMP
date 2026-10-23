package com.zhimu.server.biz.farm.api;

import com.zhimu.server.common.vo.FarmBriefVO;

import java.util.List;

/** 养殖场查询（供模块、管理端读取）：跨养殖场时使用 TenantSkip。 */
public interface FarmQueryService {

    /** 本人为有效成员的全部养殖场（含待审核、已停用；不含已删除） */
    List<FarmBriefVO> listFarmsOfUser(Long userId);
}
