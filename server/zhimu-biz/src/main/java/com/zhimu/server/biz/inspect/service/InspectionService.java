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

    /**
     * API-L01 生产概况用：某日巡检为异常的栏舍（当前成员可见范围，顺序同 I02“异常”页签）
     * 与当天是否全部巡检完成（口径同 I01 all_done）。只读一次当日清单。
     */
    DayAbnormal dayAbnormal(String date);

    /** 某日巡检异常概况 */
    record DayAbnormal(boolean allDone, List<AbnormalPen> pens) {

        public DayAbnormal {
            pens = pens == null ? List.of() : List.copyOf(pens);
        }
    }

    /** 巡检异常的栏舍；report_id 为这次巡检关联的异常上报，未上报时为空 */
    record AbnormalPen(Long penId, String penName, Long inspectionId, Long reportId) {
    }

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
