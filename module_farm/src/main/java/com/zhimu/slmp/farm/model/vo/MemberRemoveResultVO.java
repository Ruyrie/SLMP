package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.model.vo.PenBriefVO;

import java.util.Collections;
import java.util.List;

/**
 * 移出成员结果（API-F26；客户端暂定类名）：unassigned_pens 不为空时 F07 显示栏舍无负责员工横幅。
 */
public class MemberRemoveResultVO {

    @Nullable
    private List<PenBriefVO> unassignedPens;

    @NonNull
    public List<PenBriefVO> getUnassignedPens() {
        return unassignedPens == null ? Collections.emptyList() : unassignedPens;
    }
}
