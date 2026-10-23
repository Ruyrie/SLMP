package com.zhimu.server.biz.account.api;

/** 管理端停用、启用账号与手机号占用查询（ADM-S12、S13、R20）调用的 Service 方法；第 1 批不写 Controller。 */
public interface AccountAdminService {

    void disable(Long userId, Long adminId, String reason, boolean notifySms, int version);

    void enable(Long userId, Long adminId, String reason, int version);

    /** 手机号是否被占用（含注销未满 30 天、尚未释放的账号），供客服代登记校验 */
    boolean existsPhoneIncludingCooling(String phone);
}
