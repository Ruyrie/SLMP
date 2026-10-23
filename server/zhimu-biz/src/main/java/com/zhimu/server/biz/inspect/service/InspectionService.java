package com.zhimu.server.biz.inspect.service;

import com.zhimu.server.biz.inspect.query.InspectionDayQuery;
import com.zhimu.server.biz.inspect.query.InspectionFormQuery;
import com.zhimu.server.biz.inspect.query.PenHistoryQuery;
import com.zhimu.server.biz.inspect.request.InspectionBackfillRequest;
import com.zhimu.server.biz.inspect.request.InspectionSubmitRequest;
import com.zhimu.server.biz.inspect.vo.InspectionDetailVO;
import com.zhimu.server.biz.inspect.vo.InspectionFormVO;
import com.zhimu.server.biz.inspect.vo.InspectionListItemVO;
import com.zhimu.server.biz.inspect.vo.InspectionSubmitResultVO;
import com.zhimu.server.biz.inspect.vo.InspectionSummaryVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionOverviewVO;
import com.zhimu.server.biz.inspect.vo.PenInspectionRowVO;
import com.zhimu.server.biz.inspect.vo.RecentIssueVO;
import com.zhimu.server.common.api.PageVO;

import java.util.List;

/** API-I01–I03、I06–I12、I25 巡检查询与写接口。 */
public interface InspectionService {

    InspectionSummaryVO summary(String date);

    PageVO<InspectionListItemVO> dayList(InspectionDayQuery query);

    List<RecentIssueVO> recentIssues();

    InspectionFormVO formContext(InspectionFormQuery query);

    InspectionSubmitResultVO register(InspectionSubmitRequest request);

    InspectionSubmitResultVO modify(Long inspectionId, InspectionSubmitRequest request);

    InspectionSubmitResultVO backfill(InspectionBackfillRequest request);

    InspectionSubmitResultVO findByRequest(String clientRequestId);

    InspectionDetailVO detail(Long inspectionId);

    PageVO<PenInspectionRowVO> penHistory(Long penId, PenHistoryQuery query);

    PenInspectionOverviewVO penOverview(Long penId, String to);
}
