package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A10 提交找回申请的结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRecoverySubmitVO {
    private Long recoveryId;
    private String newPhoneMasked;
    private String status;
    private String statusText;
    private String submittedAt;
}
