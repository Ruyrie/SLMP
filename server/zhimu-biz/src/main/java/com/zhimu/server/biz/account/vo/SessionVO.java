package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A08 当前会话与强制步骤 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionVO {
    private AccountUserVO user;
    private String nextStep;
    private String nextStepText;
    private RecoveryInfoVO recoveryInfo;
    private Long lastFarmId;
}
