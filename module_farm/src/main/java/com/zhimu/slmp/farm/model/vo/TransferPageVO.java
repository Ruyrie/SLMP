package com.zhimu.slmp.farm.model.vo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 转让页数据（API-F28）：养殖场页头、候选场长、待确认转让（可空）。
 */
public class TransferPageVO {

    @Nullable
    private TransferFarmVO farm;
    @Nullable
    private List<TransferCandidateVO> candidates;
    @Nullable
    private TransferVO pending;

    @Nullable
    public TransferFarmVO getFarm() {
        return farm;
    }

    @NonNull
    public List<TransferCandidateVO> getCandidates() {
        return candidates == null ? Collections.emptyList() : candidates;
    }

    @Nullable
    public TransferVO getPending() {
        return pending;
    }
}
