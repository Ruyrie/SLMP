package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.FarmChangeAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmChangeDetailVO;
import com.zhimu.server.admin.audit.vo.FarmChangePageVO;

/** 资料变更审核（ADM-R06–R09）：列表、对比详情与通过/驳回。 */
public interface FarmChangeReviewService {

    FarmChangePageVO list(FarmChangeAdminQuery query);

    FarmChangeDetailVO detail(Long changeId);

    AuditResultVO approve(Long changeId, AuditApproveRequest request);

    AuditResultVO reject(Long changeId, AuditRejectRequest request);
}
