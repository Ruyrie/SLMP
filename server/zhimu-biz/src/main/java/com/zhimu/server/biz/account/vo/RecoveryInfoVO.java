package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 账号找回信息（next_step 为 RECOVERY_CHANGE_PASSWORD 时返回） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryInfoVO {
    private String newPhoneMasked;
    private String recoveredAt;
    private String cancelAvailableDate;
}
