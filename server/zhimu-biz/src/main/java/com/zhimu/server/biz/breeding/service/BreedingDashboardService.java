package com.zhimu.server.biz.breeding.service;

import com.zhimu.server.biz.breeding.query.BreedingDueQuery;
import com.zhimu.server.biz.breeding.vo.BreedingOverviewVO;
import com.zhimu.server.biz.breeding.vo.FarrowDueItemVO;
import com.zhimu.server.biz.breeding.vo.HatchDueItemVO;
import com.zhimu.server.biz.breeding.vo.PendingCheckVO;
import com.zhimu.server.biz.breeding.vo.SubmissionVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;

/** 繁育概况、待妊检、待产、待出雏与提交结果查询（API-B01、B14、B22、B23、B32）。 */
public interface BreedingDashboardService {

    BreedingOverviewVO overview();

    PageVO<PendingCheckVO> pendingChecks(PageQuery query);

    PageVO<FarrowDueItemVO> farrowDue(BreedingDueQuery query);

    PageVO<HatchDueItemVO> hatchDue(BreedingDueQuery query);

    SubmissionVO submission(String clientRequestId, String type);
}
