package com.zhimu.slmp.farm.model.request;

import androidx.annotation.Nullable;

/**
 * 修改基本信息（API-F10）：App 每次只传当前面板的一项（null 不输出），intro 清空传空字符串；version 取 basic.version。
 */
public class UpdateFarmBasicRequest {

    @Nullable
    private String contactName;
    @Nullable
    private String contactPhone;
    @Nullable
    private String intro;
    @Nullable
    private Integer scale;
    private int version;

    public UpdateFarmBasicRequest(int version) {
        this.version = version;
    }

    public UpdateFarmBasicRequest setContactName(@Nullable String contactName) {
        this.contactName = contactName;
        return this;
    }

    public UpdateFarmBasicRequest setContactPhone(@Nullable String contactPhone) {
        this.contactPhone = contactPhone;
        return this;
    }

    public UpdateFarmBasicRequest setIntro(@Nullable String intro) {
        this.intro = intro;
        return this;
    }

    public UpdateFarmBasicRequest setScale(@Nullable Integer scale) {
        this.scale = scale;
        return this;
    }

    @Nullable
    public String getContactName() {
        return contactName;
    }

    @Nullable
    public String getContactPhone() {
        return contactPhone;
    }

    @Nullable
    public String getIntro() {
        return intro;
    }

    @Nullable
    public Integer getScale() {
        return scale;
    }

    public int getVersion() {
        return version;
    }
}
