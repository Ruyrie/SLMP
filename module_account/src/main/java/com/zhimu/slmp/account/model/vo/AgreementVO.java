package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * API-A21 当前协议与客服电话（不需要登录）。
 */
public class AgreementVO {

    @Nullable
    private AgreementItemVO userAgreement;
    @Nullable
    private AgreementItemVO privacyPolicy;
    /** 2026-10-01 */
    @Nullable
    private String agreementVersion;
    /** 400-123-4567 */
    @Nullable
    private String customerServicePhone;

    @Nullable
    public AgreementItemVO getUserAgreement() {
        return userAgreement;
    }

    @Nullable
    public AgreementItemVO getPrivacyPolicy() {
        return privacyPolicy;
    }

    @Nullable
    public String getAgreementVersion() {
        return agreementVersion;
    }

    @Nullable
    public String getCustomerServicePhone() {
        return customerServicePhone;
    }
}
