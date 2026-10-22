package com.zhimu.slmp.health.ui.disinfecttask;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthItemDisinfectTaskBinding;
import com.zhimu.slmp.health.databinding.HealthItemTaskHeaderBinding;
import com.zhimu.slmp.health.model.vo.DisinfectTaskVO;
import com.zhimu.slmp.health.ui.common.NeighborRebinder;
import com.zhimu.slmp.health.ui.common.ToneStyle;
import com.zhimu.slmp.health.util.HealthFormat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * D09 任务列表：分组标题（“已逾期 · 1”，上方可带逾期提醒横幅）与任务卡（已逾期 / 待执行 / 已执行三种样式）。
 */
public class DisinfectTaskAdapter extends PagingDataAdapter<DisinfectTaskRow, RecyclerView.ViewHolder> {

    /** 任务卡操作 */
    public interface Listener {
        void onClick(@NonNull DisinfectTaskVO task);

        void onBackfill(@NonNull DisinfectTaskVO task);
    }

    private static final DiffUtil.ItemCallback<DisinfectTaskRow> DIFF = new DiffUtil.ItemCallback<DisinfectTaskRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull DisinfectTaskRow a, @NonNull DisinfectTaskRow b) {
            return a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DisinfectTaskRow a, @NonNull DisinfectTaskRow b) {
            return a.equals(b);
        }
    };

    private final Listener listener;
    private boolean staff;
    @NonNull
    private Map<String, Integer> counts = Collections.emptyMap();

    public DisinfectTaskAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new NeighborRebinder(this));
    }

    public void setStaff(boolean staff) {
        this.staff = staff;
    }

    /** 分组标题数量（tab_counts） */
    public void setCounts(@Nullable Map<String, Integer> counts) {
        this.counts = counts == null ? Collections.emptyMap() : counts;
        for (int i = 0; i < getItemCount(); i++) {
            DisinfectTaskRow r = peek(i);
            if (r != null && r.type == DisinfectTaskRow.TYPE_HEADER) {
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        DisinfectTaskRow r = peek(position);
        return r == null ? DisinfectTaskRow.TYPE_TASK : r.type == DisinfectTaskRow.TYPE_TASK
                ? DisinfectTaskRow.TYPE_TASK : DisinfectTaskRow.TYPE_HEADER;
    }

    /** 是否为任务卡（分组列表的项间缝隙只加在任务之间） */
    public boolean isTask(int position) {
        if (position < 0 || position >= getItemCount()) {
            return false;
        }
        DisinfectTaskRow r = peek(position);
        return r != null && r.type == DisinfectTaskRow.TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == DisinfectTaskRow.TYPE_TASK) {
            return new TaskVH(HealthItemDisinfectTaskBinding.inflate(inflater, parent, false));
        }
        return new HeaderVH(HealthItemTaskHeaderBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(NeighborRebinder.PAYLOAD_POSITION) && holder instanceof TaskVH) {
            GroupPosition.apply(((TaskVH) holder).b.healthCard, groupPosition(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /** 同组任务的首、中、末位置（相邻项不是任务即为组边界） */
    private int groupPosition(int position) {
        boolean first = !isTask(position - 1);
        boolean last = !isTask(position + 1);
        if (first && last) {
            return GroupPosition.SINGLE;
        }
        return first ? GroupPosition.TOP : last ? GroupPosition.BOTTOM : GroupPosition.MIDDLE;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DisinfectTaskRow row = getItem(position);
        if (row == null) {
            return;
        }
        if (holder instanceof HeaderVH) {
            bindHeader(((HeaderVH) holder).b, row);
        } else if (holder instanceof TaskVH && row.task != null) {
            HealthItemDisinfectTaskBinding b = ((TaskVH) holder).b;
            GroupPosition.apply(b.healthCard, groupPosition(position));
            bindTask(b, row.task);
        }
    }

    private void bindHeader(@NonNull HealthItemTaskHeaderBinding b, @NonNull DisinfectTaskRow row) {
        boolean hasBanner = row.banner != null;
        b.healthBanner.setVisibility(hasBanner ? View.VISIBLE : View.GONE);
        if (hasBanner) {
            b.healthBanner.setText(row.banner);
        }
        boolean hasTitle = row.type == DisinfectTaskRow.TYPE_HEADER && row.status != null;
        b.healthTitle.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        if (hasTitle) {
            Integer n = counts.get(row.status.toLowerCase(java.util.Locale.ROOT));
            b.healthTitle.setTitle(DisinfectTaskViewModel.statusLabel(row.status) + HealthFormat.DOT + (n == null ? 0 : n));
        }
    }

    private void bindTask(@NonNull HealthItemDisinfectTaskBinding b, @NonNull DisinfectTaskVO t) {
        Context ctx = b.getRoot().getContext();
        String status = HealthFormat.nonNull(t.getStatus());
        b.healthTitle.setText(t.getPenName());
        b.healthBackfill.setVisibility(View.GONE);
        b.healthExtra.setVisibility(View.GONE);
        switch (status) {
            case DisinfectTaskViewModel.OVERDUE:
                ToneStyle.apply(b.healthIcon, ToneStyle.ERROR);
                b.healthTag.setStatus(t.getStatusText() == null ? "已逾期" : t.getStatusText(), StatusTagView.ERROR);
                b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_schedule);
                b.healthSubtitle.setText("计划 " + HealthFormat.date(t.getTaskDate()) + HealthFormat.DOT
                        + HealthFormat.nonNull(t.getDisinfectantName()) + " " + HealthFormat.nonNull(t.getConcentration()));
                b.healthExtra.setVisibility(View.VISIBLE);
                if (staff) {
                    b.healthExtra.setText(R.string.health_overdue_staff);
                } else {
                    b.healthExtra.setText(withPerson(ctx, "", t.isNoStaff()
                            ? "无负责员工，由" + HealthFormat.nonNull(t.getExecutorText()) + "执行"
                            : HealthFormat.nonNull(t.getExecutorText())));
                }
                b.healthBackfill.setVisibility(!staff && t.getActions().contains(HealthExtras.ACTION_BACKFILL)
                        ? View.VISIBLE : View.GONE);
                break;
            case DisinfectTaskViewModel.DONE:
                ToneStyle.apply(b.healthIcon, ToneStyle.NEUTRAL);
                b.healthTag.setStatus(t.getStatusText() == null ? "已执行" : t.getStatusText(), StatusTagView.NEUTRAL);
                b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_check);
                String when = t.getActualTime() != null ? HealthFormat.dateTime(t.getActualTime())
                        : HealthFormat.date(t.getTaskDate());
                b.healthSubtitle.setText(withPerson(ctx, when + HealthFormat.DOT,
                        t.getActualExecutorName() != null ? t.getActualExecutorName() : HealthFormat.nonNull(t.getExecutorText())));
                break;
            default:
                ToneStyle.apply(b.healthIcon, ToneStyle.SECONDARY);
                b.healthTag.setStatus(t.getStatusText() == null ? "待执行" : t.getStatusText(), StatusTagView.PRIMARY);
                b.healthTag.setTagIcon(0);
                String day = t.getDateText() != null ? t.getDateText() : HealthFormat.dayText(t.getTaskDate());
                b.healthSubtitle.setText(withPerson(ctx, day + HealthFormat.DOT, HealthFormat.nonNull(t.getExecutorText())));
                break;
        }
        ClickThrottle.setOnClickListener(b.healthBackfill, v -> listener.onBackfill(t));
        boolean clickable = DisinfectTaskViewModel.DONE.equals(status) ? t.getRecordId() != null
                : t.getActions().contains(HealthExtras.ACTION_REGISTER)
                || (!staff && t.getActions().contains(HealthExtras.ACTION_BACKFILL));
        if (clickable) {
            ClickThrottle.setOnClickListener(b.healthCard, v -> listener.onClick(t));
        } else {
            b.healthCard.setOnClickListener(null);
            b.healthCard.setClickable(false);
        }
    }

    /** “今天 · ”+ 16 dp person 图标 +“李明” */
    @NonNull
    static CharSequence withPerson(@NonNull Context ctx, @NonNull String prefix, @NonNull String name) {
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        Drawable icon = AppCompatResources.getDrawable(ctx, com.zhimu.slmp.base.R.drawable.ic_person);
        if (icon != null) {
            icon = icon.mutate();
            int size = ctx.getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_icon_size_tag);
            icon.setBounds(0, 0, size, size);
            icon.setTintList(ColorStateList.valueOf(MaterialColors.getColor(ctx,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0)));
            int start = sb.length();
            sb.append("⁠");
            sb.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_CENTER), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(" ");
        }
        sb.append(name);
        return sb;
    }

    static final class TaskVH extends RecyclerView.ViewHolder {
        final HealthItemDisinfectTaskBinding b;

        TaskVH(@NonNull HealthItemDisinfectTaskBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final HealthItemTaskHeaderBinding b;

        HeaderVH(@NonNull HealthItemTaskHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
