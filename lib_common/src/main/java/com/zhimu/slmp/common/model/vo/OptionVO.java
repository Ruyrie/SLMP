package com.zhimu.slmp.common.model.vo;

import androidx.annotation.Nullable;

/**
 * 下拉与选择面板的选项（hint 例如“库存不足”“已停用”）。
 */
public class OptionVO {

    @Nullable
    private String value;
    @Nullable
    private String label;
    private boolean disabled;
    @Nullable
    private String hint;

    @Nullable
    public String getValue() {
        return value;
    }

    @Nullable
    public String getLabel() {
        return label;
    }

    public boolean isDisabled() {
        return disabled;
    }

    @Nullable
    public String getHint() {
        return hint;
    }
}
