package com.zhimu.slmp.health.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-M27 批号选项（复用模块09，本组件同名声明）：已过期、已停用、禁用药批号在 unselectable。
 */
public class DrugLotOptionsVO {

    @Nullable
    private List<DrugLotOptionVO> selectable;
    @Nullable
    private List<DrugLotOptionVO> unselectable;
    @Nullable
    private Long defaultLotId;

    @NonNull
    public List<DrugLotOptionVO> getSelectable() {
        return selectable == null ? Collections.emptyList() : selectable;
    }

    @NonNull
    public List<DrugLotOptionVO> getUnselectable() {
        return unselectable == null ? Collections.emptyList() : unselectable;
    }

    @Nullable
    public Long getDefaultLotId() {
        return defaultLotId;
    }
}
