package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.request.RecoveryHandleRequest;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.RecoveryDetailVO;
import com.zhimu.server.common.api.PageVO;

/** 账号找回人工核实（ADM-R14–R17）：队列、详情、通过并换绑、驳回。 */
public interface RecoveryReviewService {

    PageVO<com.zhimu.server.admin.audit.vo.RecoveryItemVO> list(RecoveryAdminQuery query);

    RecoveryDetailVO detail(Long id);

    AuditResultVO approve(Long id, RecoveryHandleRequest request);

    AuditResultVO reject(Long id, RecoveryHandleRequest request);
}
