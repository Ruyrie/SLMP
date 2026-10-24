package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.query.FarmMapQuery;
import com.zhimu.server.admin.system.query.TrendQuery;
import com.zhimu.server.admin.system.vo.FarmMapVO;
import com.zhimu.server.admin.system.vo.OverviewVO;
import com.zhimu.server.admin.system.vo.RegionDetailVO;
import com.zhimu.server.admin.system.vo.TrendVO;

/** ADM-S06–S09 平台概览：概览、分布地图、地区明细、近 30 天趋势。 */
public interface OverviewService {

    OverviewVO overview();

    FarmMapVO farmMap(FarmMapQuery query);

    RegionDetailVO regionDetail(String regionCode);

    TrendVO trend(TrendQuery query);
}
