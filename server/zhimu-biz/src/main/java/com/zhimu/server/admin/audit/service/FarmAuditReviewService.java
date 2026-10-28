package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.FarmAuditAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.FarmAuditRejectRequest;
import com.zhimu.server.admin.audit.request.SmsResendRequest;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmAuditDetailVO;
import com.zhimu.server.admin.audit.vo.FarmAuditPageVO;
import com.zhimu.server.admin.audit.vo.NoticeResendVO;

/** 养殖场注册审核（ADM-R02–R05、R18 的重发）：列表、详情、通过/驳回与重发审核结果通知。 */
public interface FarmAuditReviewService {

    FarmAuditPageVO list(FarmAuditAdminQuery query);

    FarmAuditDetailVO detail(Long auditId);

    AuditResultVO approve(Long auditId, AuditApproveRequest request);

    AuditResultVO reject(Long auditId, FarmAuditRejectRequest request);

    /** R18 重发审核结果通知；只对已处理（APPROVED / REJECTED）的申请。 */
    NoticeResendVO resendNotice(Long auditId, SmsResendRequest request);
}
