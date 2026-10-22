package com.zhimu.slmp.herd.ui.stock;

import android.view.View;

import androidx.annotation.NonNull;

import com.zhimu.slmp.herd.databinding.HerdItemBatchBinding;
import com.zhimu.slmp.herd.model.vo.BatchListItemVO;
import com.zhimu.slmp.herd.model.vo.BatchPenItem;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

/**
 * 批次行绑定（L03 批次视图、品类视图展开项共用）。
 */
final class BatchRowBinder {

    private BatchRowBinder() {
    }

    static void bind(@NonNull HerdItemBatchBinding b, @NonNull BatchListItemVO item) {
        b.herdBatchNo.setText(item.getBatchNo());
        b.herdBatchBreedingTag.setVisibility(item.isBreeding() ? View.VISIBLE : View.GONE);
        b.herdBatchCount.setText(HerdFormat.qty(item.getCurrentCount(), item.getUnitText()));
        StringBuilder sub = new StringBuilder(item.getBreedTitle() == null ? "" : item.getBreedTitle());
        for (BatchPenItem p : item.getPens()) {
            sub.append(HerdFormat.DOT).append(p.getPenName());
        }
        sub.append(HerdFormat.DOT).append(item.getDayAge()).append(" 日龄");
        if (item.getStageName() != null) {
            sub.append(HerdFormat.DOT).append(item.getStageName());
        }
        b.herdBatchSub.setText(sub);
        b.herdBatchMortality.setText("死亡率 " + HerdFormat.percent(item.getMortalityRate()));
        b.herdBatchWithdrawalTag.setVisibility(item.isInWithdrawal() ? View.VISIBLE : View.GONE);
    }
}
