package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.query.FarmListQuery;
import com.zhimu.server.biz.farm.query.NameCheckQuery;
import com.zhimu.server.biz.farm.request.FarmVersionRequest;
import com.zhimu.server.biz.farm.request.UpdateFarmBasicRequest;
import com.zhimu.server.biz.farm.vo.FarmBasicVO;
import com.zhimu.server.biz.farm.vo.FarmContextVO;
import com.zhimu.server.biz.farm.vo.FarmDetailVO;
import com.zhimu.server.biz.farm.vo.FarmListItemVO;
import com.zhimu.server.biz.farm.vo.NameCheckVO;
import com.zhimu.server.common.api.PageVO;

/** 养殖场列表、上下文、切换、名称检查、养殖场信息、停用启用、删除（API-F01–F04、F09–F12） */
public interface FarmService {

    PageVO<FarmListItemVO> listFarms(Long userId, Long currentFarmId, FarmListQuery query);

    FarmContextVO getContext(Long farmId, Long userId);

    FarmContextVO switchFarm(Long farmId, Long userId);

    NameCheckVO checkName(Long userId, NameCheckQuery query);

    FarmDetailVO detail(Long farmId, Long userId);

    FarmBasicVO updateBasic(Long farmId, Long userId, UpdateFarmBasicRequest request);

    FarmDetailVO disable(Long farmId, Long userId, FarmVersionRequest request);

    FarmDetailVO enable(Long farmId, Long userId, FarmVersionRequest request);

    /** 删除未通过审核的养殖场，返回删除前的名称（用于提示“已删除北湾牛场”） */
    String deleteFarm(Long farmId, Long userId);
}
