package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.ChangeRequestAdminQuery;
import com.zhimu.server.admin.audit.request.AuditRejectRequest;
import com.zhimu.server.admin.audit.request.ChangeRequestApproveRequest;
import com.zhimu.server.admin.audit.vo.ApproveResultVO;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminDetailVO;
import com.zhimu.server.admin.audit.vo.ChangeRequestAdminPageVO;

/** 修改申请审核（ADM-R10–R13）：队列、详情、通过并重算、驳回。 */
public interface ChangeRequestReviewService {

    ChangeRequestAdminPageVO list(ChangeRequestAdminQuery query);

    ChangeRequestAdminDetailVO detail(Long id);

    ApproveResultVO approve(Long id, ChangeRequestApproveRequest request);

    AuditResultVO reject(Long id, AuditRejectRequest request);
}
