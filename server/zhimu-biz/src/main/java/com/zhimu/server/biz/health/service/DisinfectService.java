package com.zhimu.server.biz.health.service;

import com.zhimu.server.biz.health.query.DisinfectPlanQuery;
import com.zhimu.server.biz.health.query.DisinfectTaskQuery;
import com.zhimu.server.biz.health.query.DisinfectantQuery;
import com.zhimu.server.biz.health.request.DisinfectPlanPauseRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanPreviewRequest;
import com.zhimu.server.biz.health.request.DisinfectPlanSaveRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordCreateRequest;
import com.zhimu.server.biz.health.request.DisinfectRecordUpdateRequest;
import com.zhimu.server.biz.health.vo.DisinfectPlanDetailVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanPreviewVO;
import com.zhimu.server.biz.health.vo.DisinfectPlanVO;
import com.zhimu.server.biz.health.vo.DisinfectRecordVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskOptionVO;
import com.zhimu.server.biz.health.vo.DisinfectTaskPageVO;
import com.zhimu.server.biz.health.vo.DisinfectantOptionVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** 消毒计划、任务与记录（API-D28–D40）。 */
public interface DisinfectService {

    PageVO<DisinfectPlanVO> plans(DisinfectPlanQuery query);

    DisinfectPlanDetailVO plan(Long planId);

    DisinfectPlanPreviewVO preview(DisinfectPlanPreviewRequest request);

    DisinfectPlanVO create(DisinfectPlanSaveRequest request);

    DisinfectPlanVO update(Long planId, DisinfectPlanSaveRequest request);

    DisinfectPlanVO pause(Long planId, DisinfectPlanPauseRequest request);

    DisinfectPlanVO resume(Long planId, Integer version);

    List<DisinfectantOptionVO> disinfectants(DisinfectantQuery query);

    DisinfectTaskPageVO tasks(DisinfectTaskQuery query);

    List<DisinfectTaskOptionVO> taskOptions();

    DisinfectRecordVO createRecord(DisinfectRecordCreateRequest request);

    DisinfectRecordVO record(Long recordId);

    DisinfectRecordVO updateRecord(Long recordId, DisinfectRecordUpdateRequest request);
}
