package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 字典项（API-U10，只含启用项）。
 */
public class DictItemVO {

    private long id;
    @Nullable
    private String code;
    @Nullable
    private String name;
    @Nullable
    private String description;
    /** 选中后备注必填（例如死淘原因“其他”） */
    private boolean remarkRequired;
    private boolean isSystem;
    private int sortNo;

    public long getId() {
        return id;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    @Nullable
    public String getName() {
        return name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isRemarkRequired() {
        return remarkRequired;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public int getSortNo() {
        return sortNo;
    }
}
