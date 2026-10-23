package com.zhimu.server.biz.account.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** API-A21 当前协议与客服电话 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgreementVO {
    private AgreementItemVO userAgreement;
    private AgreementItemVO privacyPolicy;
    private String agreementVersion;
    private String customerServicePhone;
}
