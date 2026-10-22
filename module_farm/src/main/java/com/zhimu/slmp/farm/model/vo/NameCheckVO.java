package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.Nullable;

/**
 * 名称重名检查（API-F04）：重名时 available 为 false，message 为“你已经有一个叫“北湾牛场”的养殖场，请换一个名称”。
 */
public class NameCheckVO {

    private boolean available;
    @Nullable
    private String message;

    public boolean isAvailable() {
        return available;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}
