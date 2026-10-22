package com.zhimu.slmp.health.ui.disinfectplan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.databinding.HealthItemDisinfectPlanBinding;
import com.zhimu.slmp.health.model.vo.DisinfectPlanVO;
import com.zhimu.slmp.health.ui.common.NeighborRebinder;
import com.zhimu.slmp.health.ui.common.ToneStyle;
import com.zhimu.slmp.health.util.HealthFormat;

import java.util.List;
import java.util.Objects;

/**
 * D06 计划卡片：覆盖栏舍（超过 2 个“等 n 个栏舍”由服务端拼好）、状态标签、“每 3 天 · 10月1日–12月31日”、
 * “聚维酮碘溶液 1:300 · 喷雾”、“下次任务 今天”或暂停说明；按钮按 actions（暂停计划 / 启用计划 / 修改计划）。
 */
public class DisinfectPlanAdapter extends PagingDataAdapter<DisinfectPlanVO, DisinfectPlanAdapter.VH> {

    /** 卡片按钮 */
    public interface Listener {
        void onPause(@NonNull DisinfectPlanVO p);

        void onResume(@NonNull DisinfectPlanVO p);

        void onEdit(@NonNull DisinfectPlanVO p);
    }

    private static final DiffUtil.ItemCallback<DisinfectPlanVO> DIFF = new DiffUtil.ItemCallback<DisinfectPlanVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull DisinfectPlanVO a, @NonNull DisinfectPlanVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull DisinfectPlanVO a, @NonNull DisinfectPlanVO b) {
            return a.getVersion() == b.getVersion() && Objects.equals(a.getStatus(), b.getStatus());
        }
    };

    private final Listener listener;
    @Nullable
    private Long busyId;

    public DisinfectPlanAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new NeighborRebinder(this));
    }

    /** 对应卡片按钮显示加载 */
    public void setBusyId(@Nullable Long id) {
        if (Objects.equals(busyId, id)) {
            return;
        }
        busyId = id;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(HealthItemDisinfectPlanBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        DisinfectPlanVO p = getItem(position);
        HealthItemDisinfectPlanBinding b = holder.b;
        GroupPosition.apply(b.healthCard, GroupPosition.of(position, getItemCount()));
        if (p == null) {
            return;
        }
        String status = HealthFormat.nonNull(p.getStatus());
        boolean active = DisinfectPlanListViewModel.ACTIVE.equals(status);
        boolean paused = DisinfectPlanListViewModel.PAUSED.equals(status);
        ToneStyle.apply(b.healthIcon, active ? ToneStyle.SECONDARY : ToneStyle.NEUTRAL);
        b.healthTitle.setText(p.getPenNamesText());
        if (active) {
            b.healthTag.setStatus(p.getStatusText() == null ? "启用中" : p.getStatusText(), StatusTagView.PRIMARY);
            b.healthTag.setTagIcon(0);
        } else if (paused) {
            b.healthTag.setStatus(p.getStatusText() == null ? "已暂停" : p.getStatusText(), StatusTagView.NEUTRAL);
            b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_pause);
        } else {
            b.healthTag.setStatus(p.getStatusText() == null ? "已结束" : p.getStatusText(), StatusTagView.NEUTRAL);
            b.healthTag.setTagIcon(0);
        }
        b.healthLine1.setText("每 " + p.getCycleDays() + " 天" + HealthFormat.DOT
                + DateFormatter.formatDateRange(p.getStartDate(), p.getEndDate()));
        b.healthLine2.setText(HealthFormat.nonNull(p.getDisinfectantName()) + " " + HealthFormat.nonNull(p.getConcentration())
                + HealthFormat.DOT + HealthFormat.nonNull(p.getMethodName()));
        String line3;
        if (paused && p.getPauseText() != null) {
            line3 = p.getPauseText();
        } else if (p.getNextTaskText() != null) {
            line3 = "下次任务 " + p.getNextTaskText();
        } else if (p.getNextTaskDate() != null) {
            line3 = "下次任务 " + HealthFormat.dayText(p.getNextTaskDate());
        } else {
            line3 = null;
        }
        b.healthLine3.setText(line3);
        b.healthLine3.setVisibility(line3 == null ? View.GONE : View.VISIBLE);

        List<String> actions = p.getActions();
        boolean busy = busyId != null && busyId == p.getId();
        b.healthPause.setVisibility(actions.contains(HealthExtras.ACTION_PAUSE) ? View.VISIBLE : View.GONE);
        b.healthResume.setVisibility(actions.contains(HealthExtras.ACTION_RESUME) ? View.VISIBLE : View.GONE);
        b.healthEdit.setVisibility(actions.contains(HealthExtras.ACTION_EDIT) ? View.VISIBLE : View.GONE);
        ButtonLoading.setLoading(b.healthResume, busy && b.healthResume.getVisibility() == View.VISIBLE);
        ClickThrottle.setOnClickListener(b.healthPause, v -> listener.onPause(p));
        ClickThrottle.setOnClickListener(b.healthResume, v -> listener.onResume(p));
        ClickThrottle.setOnClickListener(b.healthEdit, v -> listener.onEdit(p));
    }

    static final class VH extends RecyclerView.ViewHolder {
        final HealthItemDisinfectPlanBinding b;

        VH(@NonNull HealthItemDisinfectPlanBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
