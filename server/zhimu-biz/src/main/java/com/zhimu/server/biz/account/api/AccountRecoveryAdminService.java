package com.zhimu.server.biz.account.api;

import java.time.LocalDateTime;

/** 管理端找回审核（ADM-R16、R17、R18、R20、R21）调用的 Service 方法；第 1 批不写 Controller。 */
public interface AccountRecoveryAdminService {

    void approve(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    void reject(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    long countPending();

    /**
     * ADM-R18 重发找回结果短信：只允许已处理（APPROVED / REJECTED）的申请。
     * APPROVED 时重新生成临时密码（旧临时密码立即失效），把 result_sms_status 重置为 PENDING，
     * 由短信任务发送；不返回、不记录临时密码明文。
     */
    SmsResendResult resendResultSms(Long recoveryId, Long adminId, String reason);

    /**
     * ADM-R20 客服代登记：按账号当前手机号与恢复码核验身份，写入 PENDING 申请；
     * submit_channel = CS_PROXY，proxy_admin_id = 当前管理员，同时写一条恢复码校验记录。
     */
    ProxyCreateResult proxyCreate(Long adminId, String phone, String newPhone, String recoveryCode,
                                  String description);

    record SmsResendResult(Long id, String phone, String smsStatus, Integer retryCount,
                           LocalDateTime queuedAt, String note) {
    }

    record ProxyCreateResult(Long id, Long userId, String userName, String oldPhone, String newPhone,
                             String status, LocalDateTime submittedAt) {
    }
}
