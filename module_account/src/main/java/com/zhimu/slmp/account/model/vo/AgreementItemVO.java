package com.zhimu.slmp.account.model.vo;

import androidx.annotation.Nullable;

/**
 * 协议条目：版本、标题、HTTPS 地址。
 */
public class AgreementItemVO {

    @Nullable
    private String version;
    /** 《用户协议》 */
    @Nullable
    private String title;
    @Nullable
    private String url;

    @Nullable
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getUrl() {
        return url;
    }
}
