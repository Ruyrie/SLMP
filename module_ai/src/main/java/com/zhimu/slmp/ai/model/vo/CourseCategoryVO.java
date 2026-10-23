package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API-K14 课程分类
 */
public class CourseCategoryVO {

    private long id;
    private String name;
    private int sortNo;

    public long getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public int getSortNo() {
        return sortNo;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public void setSortNo(int sortNo) {
        this.sortNo = sortNo;
    }
}
