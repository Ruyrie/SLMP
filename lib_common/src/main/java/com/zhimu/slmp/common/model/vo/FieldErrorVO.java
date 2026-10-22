package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 参数错误（40001）时 data.field_errors 中的一项。
 */
public class FieldErrorVO {

    @Nullable
    private String field;
    @Nullable
    private String message;

    @Nullable
    public String getField() {
        return field;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}
