package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.FarmAuditAdminQuery;
import com.zhimu.server.admin.audit.request.AuditApproveRequest;
import com.zhimu.server.admin.audit.request.FarmAuditRejectRequest;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.FarmAuditDetailVO;
import com.zhimu.server.admin.audit.vo.FarmAuditPageVO;

/** 养殖场注册审核（ADM-R02–R05）：列表、详情与通过/驳回编排。 */
public interface FarmAuditReviewService {

    FarmAuditPageVO list(FarmAuditAdminQuery query);

    FarmAuditDetailVO detail(Long auditId);

    AuditResultVO approve(Long auditId, AuditApproveRequest request);

    AuditResultVO reject(Long auditId, FarmAuditRejectRequest request);
}
