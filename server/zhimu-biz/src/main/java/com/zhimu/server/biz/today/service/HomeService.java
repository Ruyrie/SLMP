package com.zhimu.server.biz.today.service;

import com.zhimu.server.biz.today.vo.HomeVO;

/** 首页聚合（API-T01）。 */
public interface HomeService {

    HomeVO getHome(Long userId);
}
