package com.zhimu.slmp.health.ui.immuneplan;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.health.HealthExtras;
import com.zhimu.slmp.health.R;
import com.zhimu.slmp.health.databinding.HealthItemImmunePlanBinding;
import com.zhimu.slmp.health.model.vo.ImmunePlanVO;
import com.zhimu.slmp.health.ui.common.NeighborRebinder;
import com.zhimu.slmp.health.ui.common.ToneStyle;
import com.zhimu.slmp.health.util.HealthFormat;
import com.zhimu.slmp.health.util.ImmuneRules;

import java.util.List;
import java.util.Objects;

/**
 * D01 免疫计划列表（分组列表，首末项圆角 20、项间 2 dp）：已接种 / 漏免 / 待接种 / 已延期 / 已取消五种样式。
 * 按钮与菜单按 actions 显示（员工只有 REGISTER）。
 */
public class ImmunePlanAdapter extends PagingDataAdapter<ImmunePlanVO, ImmunePlanAdapter.VH> {

    /** 卡片操作 */
    public interface Listener {
        void onAction(@NonNull ImmunePlanVO plan, @NonNull String action);

        void onMore(@NonNull View anchor, @NonNull ImmunePlanVO plan);

        void onClick(@NonNull ImmunePlanVO plan);
    }

    private static final DiffUtil.ItemCallback<ImmunePlanVO> DIFF = new DiffUtil.ItemCallback<ImmunePlanVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull ImmunePlanVO a, @NonNull ImmunePlanVO b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ImmunePlanVO a, @NonNull ImmunePlanVO b) {
            return a.getVersion() == b.getVersion() && Objects.equals(a.getStatus(), b.getStatus())
                    && Objects.equals(a.getPlanDate(), b.getPlanDate());
        }
    };

    private final Listener listener;
    private boolean staff;
    @NonNull
    private String unit = "头";
    @Nullable
    private Long highlightId;

    public ImmunePlanAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new NeighborRebinder(this));
    }

    public void setStaff(boolean staff) {
        this.staff = staff;
    }

    public void setUnit(@NonNull String unit) {
        this.unit = unit;
    }

    /** 新增或改期后的项目背景高亮 1 秒 */
    public void highlight(@Nullable Long planId) {
        highlightId = planId;
        if (planId == null) {
            return;
        }
        for (int i = 0; i < getItemCount(); i++) {
            ImmunePlanVO p = peek(i);
            if (p != null && p.getId() == planId) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(HealthItemImmunePlanBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        ImmunePlanVO p = getItem(position);
        HealthItemImmunePlanBinding b = holder.b;
        GroupPosition.apply(b.healthCard, GroupPosition.of(position, getItemCount()));
        if (p == null) {
            return;
        }
        String status = HealthFormat.nonNull(p.getStatus());
        b.healthTitle.setText(HealthFormat.nonNull(p.getVaccineName())
                + (p.getImmuneRound() == null ? "" : HealthFormat.DOT + p.getImmuneRound()));
        boolean cancelled = ImmuneRules.STATUS_CANCELLED.equals(status);
        b.healthTitle.setPaintFlags(cancelled ? b.healthTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : b.healthTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        b.healthTitle.setTextColor(MaterialColors.getColor(b.healthTitle, cancelled
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface));

        List<String> actions = p.getActions();
        boolean canRegister = actions.contains(HealthExtras.ACTION_REGISTER);
        boolean canPostpone = actions.contains(HealthExtras.ACTION_POSTPONE) && !staff;
        boolean canBackfill = actions.contains(HealthExtras.ACTION_BACKFILL) && !staff;
        boolean hasMenu = !staff && (actions.contains(HealthExtras.ACTION_EDIT) || actions.contains(HealthExtras.ACTION_DELETE));
        boolean showButtons = false;
        b.healthRemind.setVisibility(View.GONE);
        b.healthBackfill.setVisibility(View.GONE);

        switch (status) {
            case ImmuneRules.STATUS_DONE:
                ToneStyle.apply(b.healthIcon, ToneStyle.NEUTRAL);
                b.healthTag.setStatus(HealthFormat.nonNull(p.getStatusText()).isEmpty() ? "已接种" : p.getStatusText(),
                        StatusTagView.NEUTRAL);
                b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_check);
                StringBuilder done = new StringBuilder(HealthFormat.date(p.getRecordDate() != null ? p.getRecordDate()
                        : p.getPlanDate()));
                if (p.getRecordUserName() != null) {
                    done.append(HealthFormat.DOT).append(p.getRecordUserName());
                }
                if (p.getRecordQuantity() != null) {
                    done.append(HealthFormat.DOT).append(HealthFormat.qty(p.getRecordQuantity(), unit));
                }
                if (Boolean.TRUE.equals(p.getBackfill())) {
                    done.append(HealthFormat.DOT).append("补免");
                }
                if (Boolean.TRUE.equals(p.getManualDone())) {
                    done.append(HealthFormat.DOT).append("已手动标记完成");
                }
                b.healthSubtitle.setText(done);
                break;
            case ImmuneRules.STATUS_MISSED:
                ToneStyle.apply(b.healthIcon, ToneStyle.ERROR);
                b.healthTag.setStatus("漏免", StatusTagView.ERROR);
                b.healthTag.setTagIcon(com.zhimu.slmp.base.R.drawable.ic_error);
                b.healthSubtitle.setText(HealthFormat.date(p.getPlanDate()) + HealthFormat.DOT + "超过 "
                        + ImmuneRules.MISSED_DAYS + " 天未登记" + (staff ? "，需所有者或场长补录" : ""));
                b.healthBackfill.setVisibility(canBackfill ? View.VISIBLE : View.GONE);
                break;
            case ImmuneRules.STATUS_POSTPONED:
                ToneStyle.apply(b.healthIcon, ToneStyle.WARNING);
                b.healthTag.setStatus("已延期", StatusTagView.WARNING);
                b.healthTag.setTagIcon(R.drawable.health_ic_event_repeat);
                String from = p.getPostponeFromDate() == null ? "" : HealthFormat.date(p.getPostponeFromDate())
                        + HealthFormat.ARROW;
                b.healthSubtitle.setText(from + HealthFormat.date(p.getPlanDate())
                        + (p.getPostponedByName() == null ? "" : HealthFormat.DOT + p.getPostponedByName() + "延期"));
                showButtons = canRegister;
                break;
            case ImmuneRules.STATUS_CANCELLED:
                ToneStyle.apply(b.healthIcon, ToneStyle.NEUTRAL);
                b.healthTag.setStatus("已取消", StatusTagView.NEUTRAL);
                b.healthTag.setTagIcon(0);
                String by = "BATCH_ENDED".equals(p.getCancelType()) ? "批次结束自动取消"
                        : HealthFormat.nonNull(p.getCancelledByName()) + "已删除";
                b.healthSubtitle.setText("原计划 " + HealthFormat.date(p.getPlanDate()) + HealthFormat.DOT + by);
                break;
            default:
                ToneStyle.apply(b.healthIcon, ToneStyle.SECONDARY);
                b.healthTag.setStatus("待接种", StatusTagView.PRIMARY);
                b.healthTag.setTagIcon(0);
                b.healthSubtitle.setText((p.isToday() ? "今天" : HealthFormat.date(p.getPlanDate())) + HealthFormat.DOT
                        + p.getAgeAtPlan() + " 日龄" + HealthFormat.DOT + HealthFormat.nonNull(p.getResponsibleText()));
                if (p.getRemindText() != null && !staff) {
                    b.healthRemind.setText(p.getRemindText());
                    b.healthRemind.setVisibility(View.VISIBLE);
                }
                showButtons = canRegister || canPostpone;
                break;
        }
        boolean pendingLike = ImmuneRules.STATUS_PENDING.equals(status) || ImmuneRules.STATUS_POSTPONED.equals(status);
        b.healthButtons.setVisibility(showButtons ? View.VISIBLE : View.GONE);
        b.healthBtnRegister.setVisibility(canRegister ? View.VISIBLE : View.GONE);
        b.healthBtnPostpone.setVisibility(canPostpone && pendingLike ? View.VISIBLE : View.GONE);
        b.healthMore.setVisibility(hasMenu && pendingLike ? View.VISIBLE : View.GONE);

        ClickThrottle.setOnClickListener(b.healthBtnRegister, v -> listener.onAction(p, HealthExtras.ACTION_REGISTER));
        ClickThrottle.setOnClickListener(b.healthBtnPostpone, v -> listener.onAction(p, HealthExtras.ACTION_POSTPONE));
        ClickThrottle.setOnClickListener(b.healthBackfill, v -> listener.onAction(p, HealthExtras.ACTION_BACKFILL));
        ClickThrottle.setOnClickListener(b.healthMore, v -> listener.onMore(v, p));
        boolean clickable = ImmuneRules.STATUS_DONE.equals(status) && p.getRecordId() != null;
        if (clickable) {
            ClickThrottle.setOnClickListener(b.healthCard, v -> listener.onClick(p));
        } else {
            b.healthCard.setOnClickListener(null);
            b.healthCard.setClickable(false);
        }

        int normal = MaterialColors.getColor(b.healthCard, com.google.android.material.R.attr.colorSurfaceContainerLowest);
        if (highlightId != null && highlightId == p.getId()) {
            b.healthCard.setCardBackgroundColor(MaterialColors.getColor(b.healthCard,
                    com.google.android.material.R.attr.colorPrimaryContainer));
            highlightId = null;
            b.healthCard.postDelayed(() -> b.healthCard.setCardBackgroundColor(normal), 1000);
        } else {
            b.healthCard.setCardBackgroundColor(normal);
        }
    }

    static final class VH extends RecyclerView.ViewHolder {
        final HealthItemImmunePlanBinding b;

        VH(@NonNull HealthItemImmunePlanBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
