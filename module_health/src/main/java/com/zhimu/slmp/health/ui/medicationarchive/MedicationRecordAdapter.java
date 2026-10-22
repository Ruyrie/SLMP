package com.zhimu.slmp.health.ui.medicationarchive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.databinding.HealthItemMedicationRecordBinding;
import com.zhimu.slmp.health.model.vo.MedicationRecordVO;
import com.zhimu.slmp.health.ui.common.NeighborRebinder;
import com.zhimu.slmp.health.ui.common.ToneStyle;
import com.zhimu.slmp.health.util.HealthFormat;

import java.util.List;
import java.util.Objects;

/**
 * D05 用药记录（分组列表）：“氟苯尼考粉 · 混饲”“10月5日–10月9日 · 5 天 · 李明登记”“原因：咳嗽、采食下降”；
 * 待审核标签（warning）、已锁定标签（中性 + lock）；actions 为空时不显示 more_vert（员工他人记录）。
 */
public class MedicationRecordAdapter extends PagingDataAdapter<MedicationRecordVO, MedicationRecordAdapter.VH> {

    /** 点 more_vert */
    public interface Listener {
        void onMore(@NonNull View anchor, @NonNull MedicationRecordVO record);
    }

    private static final DiffUtil.ItemCallback<MedicationRecordVO> DIFF = new DiffUtil.ItemCallback<MedicationRecordVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull MedicationRecordVO a, @NonNull MedicationRecordVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull MedicationRecordVO a, @NonNull MedicationRecordVO b) {
            return a.getVersion() == b.getVersion() && a.isLocked() == b.isLocked()
                    && Objects.equals(pending(a), pending(b));
        }
    };

    private final Listener listener;

    public MedicationRecordAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new NeighborRebinder(this));
    }

    static Long pending(@NonNull MedicationRecordVO r) {
        return r.getChangeState() == null ? null : r.getChangeState().getPendingRequestId();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(HealthItemMedicationRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(NeighborRebinder.PAYLOAD_POSITION)) {
            GroupPosition.apply(holder.b.healthCard, GroupPosition.of(position, getItemCount()));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MedicationRecordVO r = getItem(position);
        HealthItemMedicationRecordBinding b = holder.b;
        GroupPosition.apply(b.healthCard, GroupPosition.of(position, getItemCount()));
        if (r == null) {
            return;
        }
        ToneStyle.apply(b.healthIcon, ToneStyle.SECONDARY);
        b.healthTitle.setText(HealthFormat.nonNull(r.getDrugName())
                + (r.getRouteName() == null ? "" : HealthFormat.DOT + r.getRouteName()));
        String who = r.isMine() ? "你" : HealthFormat.nonNull(r.getCreatorName());
        b.healthSubtitle.setText(DateFormatter.formatDateRange(r.getFirstDate(), r.getLastDate()) + HealthFormat.DOT
                + r.getCourseDays() + " 天" + HealthFormat.DOT + who + "登记");
        b.healthReason.setText("原因：" + HealthFormat.nonNull(r.getReason()));
        if (pending(r) != null) {
            b.healthTag.setStatus("待审核", StatusTagView.WARNING);
            b.healthTag.setTagIcon(0);
            b.healthTag.setVisibility(View.VISIBLE);
        } else if (r.isLocked()) {
            b.healthTag.setStatus("已锁定", StatusTagView.NEUTRAL);
            b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_lock);
            b.healthTag.setVisibility(View.VISIBLE);
        } else {
            b.healthTag.setVisibility(View.GONE);
        }
        boolean hasMenu = !r.getActions().isEmpty();
        b.healthMore.setVisibility(hasMenu ? View.VISIBLE : View.GONE);
        ClickThrottle.setOnClickListener(b.healthMore, v -> listener.onMore(v, r));
    }

    static final class VH extends RecyclerView.ViewHolder {
        final HealthItemMedicationRecordBinding b;

        VH(@NonNull HealthItemMedicationRecordBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
