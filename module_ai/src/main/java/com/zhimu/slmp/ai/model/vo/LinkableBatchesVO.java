package com.zhimu.slmp.ai.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * API-K05 关联批次面板：说明与空状态按角色由服务端返回
 */
public class LinkableBatchesVO {

    private List<LinkedBatchVO> batches;
    private String panelHint;
    private String emptyHint;

    @NonNull
    public List<LinkedBatchVO> getBatches() {
        return batches == null ? Collections.emptyList() : batches;
    }

    @Nullable
    public String getPanelHint() {
        return panelHint;
    }

    @Nullable
    public String getEmptyHint() {
        return emptyHint;
    }

    public void setBatches(@Nullable List<LinkedBatchVO> batches) {
        this.batches = batches;
    }

    public void setPanelHint(@Nullable String panelHint) {
        this.panelHint = panelHint;
    }

    public void setEmptyHint(@Nullable String emptyHint) {
        this.emptyHint = emptyHint;
    }
}
