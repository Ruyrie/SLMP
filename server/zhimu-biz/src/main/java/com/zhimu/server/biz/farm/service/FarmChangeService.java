package com.zhimu.server.biz.farm.service;

import com.zhimu.server.biz.farm.request.SubmitFarmChangeRequest;
import com.zhimu.server.biz.farm.vo.FarmChangeVO;

/** 资料变更（API-F13、F14） */
public interface FarmChangeService {

    /** 最近一次资料变更；没有记录返回 null（App 用 Maybe） */
    FarmChangeVO latest(Long farmId, Long userId);

    FarmChangeVO submit(Long farmId, Long userId, SubmitFarmChangeRequest request);
}
