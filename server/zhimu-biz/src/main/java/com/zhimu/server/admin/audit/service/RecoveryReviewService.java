package com.zhimu.server.admin.audit.service;

import com.zhimu.server.admin.audit.query.RecoveryAdminQuery;
import com.zhimu.server.admin.audit.request.RecoveryHandleRequest;
import com.zhimu.server.admin.audit.request.RecoveryProxyRequest;
import com.zhimu.server.admin.audit.request.SmsResendRequest;
import com.zhimu.server.admin.audit.vo.AuditResultVO;
import com.zhimu.server.admin.audit.vo.ProxyRecoveryVO;
import com.zhimu.server.admin.audit.vo.RecoveryDetailVO;
import com.zhimu.server.admin.audit.vo.RecoveryVerificationRecordsVO;
import com.zhimu.server.admin.audit.vo.RecoveryTempPasswordVO;
import com.zhimu.server.common.api.PageVO;

/** 账号找回人工核实（ADM-R14–R18、R20–R21）：队列、详情、通过并换绑、驳回、重新生成临时密码、代登记、校验记录。 */
public interface RecoveryReviewService {

    PageVO<com.zhimu.server.admin.audit.vo.RecoveryItemVO> list(RecoveryAdminQuery query);

    RecoveryDetailVO detail(Long id);

    AuditResultVO approve(Long id, RecoveryHandleRequest request);

    AuditResultVO reject(Long id, RecoveryHandleRequest request);

    /** R18 重新生成临时密码（只对已通过申请），明文只在响应里返回一次。 */
    RecoveryTempPasswordVO regenerateTempPassword(Long id, SmsResendRequest request);

    /** R21 恢复码校验记录（表无设备字段时由提交渠道给出设备文字）。 */
    RecoveryVerificationRecordsVO verificationRecords(Long id);

    /** R20 管理员代用户登记找回申请。 */
    ProxyRecoveryVO proxy(RecoveryProxyRequest request);
}
