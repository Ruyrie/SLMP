package com.zhimu.server.biz.account.api;

import java.time.LocalDateTime;

/** 管理端找回审核（ADM-R16、R17、R18、R20、R21）调用的 Service 方法；第 1 批不写 Controller。 */
public interface AccountRecoveryAdminService {

    /**
     * ADM-R16 通过：换绑手机号并生成临时密码。平台没有通知类短信模板，临时密码明文只在本次返回值里交给
     * 管理端页面一次性显示，由管理员线下告知用户；不落库、不写日志。
     */
    ApproveResult approve(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    /** ADM-R17 驳回：结果写入用户的消息中心，不发短信。 */
    void reject(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    long countPending();

    /**
     * ADM-R18 重新生成临时密码：只允许已通过（APPROVED）的申请。旧临时密码与会话立即失效，
     * 新密码明文只在返回值里交给管理端页面一次性显示；不落库、不写日志。
     */
    TempPasswordResult regenerateTempPassword(Long recoveryId, Long adminId, String reason);

    /**
     * ADM-R20 客服代登记：按账号当前手机号与恢复码核验身份，写入 PENDING 申请；
     * submit_channel = CS_PROXY，proxy_admin_id = 当前管理员，同时写一条恢复码校验记录。
     */
    ProxyCreateResult proxyCreate(Long adminId, String phone, String newPhone, String recoveryCode,
                                  String description);

    record ApproveResult(String tempPassword, LocalDateTime tempPasswordExpireAt) {
    }

    record TempPasswordResult(Long id, String phone, String tempPassword, LocalDateTime tempPasswordExpireAt,
                              LocalDateTime regeneratedAt) {
    }

    record ProxyCreateResult(Long id, Long userId, String userName, String oldPhone, String newPhone,
                             String status, LocalDateTime submittedAt) {
    }
}
