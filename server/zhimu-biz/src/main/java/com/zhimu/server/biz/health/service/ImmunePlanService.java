package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.query.ImmunePlanOptionQuery;
import com.zhimu.server.biz.health.query.ImmunePlanQuery;
import com.zhimu.server.biz.health.request.ImmunePlanCreateRequest;
import com.zhimu.server.biz.health.request.ImmunePlanPostponeRequest;
import com.zhimu.server.biz.health.request.ImmunePlanUpdateRequest;
import com.zhimu.server.biz.health.vo.ImmunePlanOptionVO;
import com.zhimu.server.biz.health.vo.ImmunePlanOverviewVO;
import com.zhimu.server.biz.health.vo.ImmunePlanVO;
import com.zhimu.server.biz.health.vo.ImmuneRegisterFormVO;
import com.zhimu.server.biz.health.vo.VaccineOptionVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 免疫计划（API-D01–D13）。 */
public interface ImmunePlanService {

    ImmunePlanOverviewVO overview(Long batchId);

    PageVO<ImmunePlanVO> list(Long batchId, ImmunePlanQuery query);

    PageVO<ImmunePlanOptionVO> options(ImmunePlanOptionQuery query);

    ImmuneRegisterFormVO registerForm(Long planId, Boolean backfill);

    List<VaccineOptionVO> vaccineOptions(Long categoryId);

    ImmunePlanOverviewVO preEntryConfirm(Long planId, Integer version);

    ImmunePlanVO preEntryJoin(Long planId, Integer version);

    ImmunePlanOverviewVO preEntryUndo(Long planId, Integer version);

    ImmunePlanVO postpone(Long planId, ImmunePlanPostponeRequest request);

    ImmunePlanVO create(Long batchId, ImmunePlanCreateRequest request);

    ImmunePlanVO update(Long planId, ImmunePlanUpdateRequest request);

    void delete(Long planId, Integer version);
}
