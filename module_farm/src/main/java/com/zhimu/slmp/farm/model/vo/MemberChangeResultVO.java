package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 修改角色、调整栏舍的结果（API-F23、F24）。
 */
public class MemberChangeResultVO {

    @Nullable
    private MemberDetailVO member;
    @Nullable
    private List<PenBriefVO> newlyUnassignedPens;

    @Nullable
    public MemberDetailVO getMember() {
        return member;
    }

    @NonNull
    public List<PenBriefVO> getNewlyUnassignedPens() {
        return newlyUnassignedPens == null ? Collections.emptyList() : newlyUnassignedPens;
    }
}
