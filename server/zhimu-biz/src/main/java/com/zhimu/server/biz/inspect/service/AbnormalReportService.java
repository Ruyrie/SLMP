package com.zhimu.server.biz.inspect.service;

import com.zhimu.server.biz.inspect.query.LinkableQuery;
import com.zhimu.server.biz.inspect.query.ReportFormQuery;
import com.zhimu.server.biz.inspect.request.AbnormalReportCreateRequest;
import com.zhimu.server.biz.inspect.request.HandleSaveRequest;
import com.zhimu.server.biz.inspect.vo.DiagnosisDetailVO;
import com.zhimu.server.biz.inspect.vo.DiagnosisStateVO;
import com.zhimu.server.biz.inspect.vo.HandleSaveResultVO;
import com.zhimu.server.biz.inspect.vo.LinkableReportVO;
import com.zhimu.server.biz.inspect.vo.ReportCreateResultVO;
import com.zhimu.server.biz.inspect.vo.ReportFormVO;
import com.zhimu.server.biz.inspect.vo.SymptomVO;

import java.util.List;

/** API-I14–I16、I19–I22、I24、I26 异常上报与 AI 诊断。 */
public interface AbnormalReportService {

    ReportFormVO formContext(ReportFormQuery query);

    List<SymptomVO> symptoms(Long batchId);

    ReportCreateResultVO create(AbnormalReportCreateRequest request);

    ReportCreateResultVO findByRequest(String clientRequestId);

    DiagnosisDetailVO detail(Long reportId);

    DiagnosisStateVO state(Long reportId);

    DiagnosisStateVO rediagnose(Long reportId);

    HandleSaveResultVO saveHandle(Long reportId, HandleSaveRequest request);

    List<LinkableReportVO> linkable(LinkableQuery query);
}
