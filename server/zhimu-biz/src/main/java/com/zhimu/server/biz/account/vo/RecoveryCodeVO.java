package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 恢复码信息：明文只在 A03、A11、A12、A13 的响应中出现一次。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryCodeVO {
    private Long recoveryCodeId;
    private String code;
    private String generatedAt;
    private String phoneMasked;
    private String scene;
    private String sceneText;
    private Boolean isRegenerated;
}
