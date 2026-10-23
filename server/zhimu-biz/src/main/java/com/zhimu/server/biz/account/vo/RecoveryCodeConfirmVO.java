package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A14 确认恢复码已保存的结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryCodeConfirmVO {
    private Long recoveryCodeId;
    private String status;
    private String confirmedAt;
    private String nextStep;
}
