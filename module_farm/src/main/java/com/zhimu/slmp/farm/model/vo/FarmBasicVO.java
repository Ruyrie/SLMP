package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 基本信息（FarmDetailVO.basic、API-F10 响应，含新 version）。
 */
public class FarmBasicVO {

    @Nullable
    private String contactName;
    @Nullable
    private String contactPhone;
    @Nullable
    private String contactPhoneMasked;
    @Nullable
    private String intro;
    private int scale;
    private int version;

    @Nullable
    public String getContactName() {
        return contactName;
    }

    @Nullable
    public String getContactPhone() {
        return contactPhone;
    }

    @Nullable
    public String getContactPhoneMasked() {
        return contactPhoneMasked;
    }

    @Nullable
    public String getIntro() {
        return intro;
    }

    public int getScale() {
        return scale;
    }

    public int getVersion() {
        return version;
    }
}
