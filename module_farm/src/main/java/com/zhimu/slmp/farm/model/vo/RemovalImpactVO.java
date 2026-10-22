package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 移出影响（API-F25）：orphan_pens 为移出后没有其他员工负责的栏舍（只对员工）。
 */
public class RemovalImpactVO {

    private long memberId;
    @Nullable
    private String memberName;
    @Nullable
    private String role;
    private int pendingWorkTodoCount;
    @Nullable
    private List<PenBriefVO> orphanPens;

    public long getMemberId() {
        return memberId;
    }

    @Nullable
    public String getMemberName() {
        return memberName;
    }

    @Nullable
    public String getRole() {
        return role;
    }

    public int getPendingWorkTodoCount() {
        return pendingWorkTodoCount;
    }

    @NonNull
    public List<PenBriefVO> getOrphanPens() {
        return orphanPens == null ? Collections.emptyList() : orphanPens;
    }
}
