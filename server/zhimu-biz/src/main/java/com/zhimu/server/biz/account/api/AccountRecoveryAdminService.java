package com.zhimu.server.biz.account.api;

/** 管理端找回审核（ADM-R16、R17、R21）调用的 Service 方法；第 1 批不写 Controller。 */
public interface AccountRecoveryAdminService {

    void approve(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    void reject(Long recoveryId, Long adminId, String verifyMethod, String handleNote, int version);

    long countPending();
}
