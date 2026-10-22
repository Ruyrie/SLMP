package com.zhimu.slmp.herd.ui.mypens;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.herd.databinding.HerdItemMyPenBatchBinding;
import com.zhimu.slmp.herd.databinding.HerdItemMyPenBinding;
import com.zhimu.slmp.herd.databinding.HerdItemMyPenTaskBinding;
import com.zhimu.slmp.herd.model.vo.MyPenBatchVO;
import com.zhimu.slmp.herd.model.vo.MyPenItemVO;
import com.zhimu.slmp.herd.model.vo.TodoBriefVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;

import java.util.List;
import java.util.Objects;

/**
 * L02 负责栏舍卡：列表第 1 项为完整样式（批次子卡 + 指标行），其余为紧凑样式（头部次行合并批次信息）。
 */
public class MyPenAdapter extends ListAdapter<MyPenItemVO, MyPenAdapter.Holder> {

    /** 点击回调 */
    public interface Listener {
        void onPenHeaderClick(@NonNull MyPenItemVO pen);

        void onBatchClick(@NonNull MyPenBatchVO batch);

        void onTaskClick(@NonNull TodoBriefVO task, @NonNull MyPenItemVO pen);
    }

    private static final int TYPE_FULL = 0;
    private static final int TYPE_COMPACT = 1;

    private final Listener listener;

    public MyPenAdapter(@NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<MyPenItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull MyPenItemVO a, @NonNull MyPenItemVO b) {
                return a.getPenId() == b.getPenId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull MyPenItemVO a, @NonNull MyPenItemVO b) {
                return a.getStockCount() == b.getStockCount() && a.getTaskDone() == b.getTaskDone()
                        && a.getTaskTotal() == b.getTaskTotal() && a.getTasks().size() == b.getTasks().size()
                        && Objects.equals(a.getPenName(), b.getPenName());
            }
        });
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_FULL : TYPE_COMPACT;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(HerdItemMyPenBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MyPenItemVO pen = getItem(position);
        boolean full = getItemViewType(position) == TYPE_FULL;
        HerdItemMyPenBinding b = holder.binding;
        Context ctx = b.getRoot().getContext();
        String unit = HerdFormat.unit(pen.getUnitText());
        List<MyPenBatchVO> batches = pen.getBatches();
        boolean empty = pen.isEmpty() || batches.isEmpty();

        b.herdPenName.setText(pen.getPenName());
        b.herdPenEmptyTag.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.herdPenChevron.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        String typeText = pen.getPenTypeText() == null ? "" : pen.getPenTypeText();
        String capacityText = "容量 " + HerdFormat.qty(pen.getCapacity(), unit);
        if (empty) {
            b.herdPenSubtitle.setText(capacityText);
        } else if (full) {
            b.herdPenSubtitle.setText(typeText.isEmpty() ? capacityText : typeText + HerdFormat.DOT + capacityText);
        } else if (batches.size() == 1) {
            MyPenBatchVO one = batches.get(0);
            b.herdPenSubtitle.setText(one.getBatchNo() + "  存栏 " + HerdFormat.qty(pen.getStockCount(), unit)
                    + HerdFormat.DOT + one.getDayAge() + " 日龄");
        } else {
            b.herdPenSubtitle.setText(batches.size() + " 个批次" + HerdFormat.DOT + "存栏 "
                    + HerdFormat.qty(pen.getStockCount(), unit));
        }
        if (empty) {
            b.herdPenHeader.setOnClickListener(null);
            b.herdPenHeader.setClickable(false);
        } else {
            ClickThrottle.setOnClickListener(b.herdPenHeader, v -> listener.onPenHeaderClick(pen));
        }

        // 批次子卡与指标行（完整样式）
        b.herdPenBatches.removeAllViews();
        boolean showDetail = full && !empty;
        b.herdPenBatches.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        b.herdPenMetrics.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        if (showDetail) {
            boolean multi = batches.size() > 1;
            LayoutInflater inflater = LayoutInflater.from(ctx);
            for (MyPenBatchVO batch : batches) {
                HerdItemMyPenBatchBinding sb = HerdItemMyPenBatchBinding.inflate(inflater, b.herdPenBatches, false);
                sb.herdBatchNo.setText(batch.getBatchNo());
                String sub = "在栏批次" + HerdFormat.DOT + batch.getBreedTitle();
                if (multi) {
                    sub = batch.getBreedTitle() + HerdFormat.DOT + batch.getDayAge() + " 日龄"
                            + (batch.getStageName() == null ? "" : HerdFormat.DOT + batch.getStageName());
                }
                sb.herdBatchSubtitle.setText(sub);
                if (batch.isInWithdrawal()) {
                    Integer days = batch.getWithdrawalRemainingDays();
                    sb.herdBatchTag.setText(days == null ? "休药中" : "休药中 · 剩 " + days + " 天");
                    sb.herdBatchTag.setVisibility(View.VISIBLE);
                }
                ClickThrottle.setOnClickListener(sb.getRoot(), v -> listener.onBatchClick(batch));
                b.herdPenBatches.addView(sb.getRoot());
            }
            bindMetric(b, ctx, pen, unit, multi ? null : batches.get(0));
        }

        // 今日任务（空栏不显示）
        b.herdPenTaskSection.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) {
            bindTasks(b, ctx, pen);
        }
    }

    private static void bindMetric(@NonNull HerdItemMyPenBinding b, @NonNull Context ctx, @NonNull MyPenItemVO pen,
                                   @NonNull String unit, MyPenBatchVO single) {
        b.herdMetricStock.herdMetricLabel.setText(com.zhimu.slmp.herd.R.string.herd_my_pens_metric_stock);
        b.herdMetricStock.herdMetricValue.setText(HerdFormat.number(pen.getStockCount()));
        b.herdMetricStock.herdMetricUnit.setText(unit);
        boolean hasSingle = single != null;
        b.herdMetricDayAge.getRoot().setVisibility(hasSingle ? View.VISIBLE : View.INVISIBLE);
        b.herdMetricStage.getRoot().setVisibility(hasSingle ? View.VISIBLE : View.INVISIBLE);
        if (hasSingle) {
            b.herdMetricDayAge.herdMetricLabel.setText(com.zhimu.slmp.herd.R.string.herd_my_pens_metric_day_age);
            b.herdMetricDayAge.herdMetricValue.setText(String.valueOf(single.getDayAge()));
            b.herdMetricDayAge.herdMetricUnit.setText(ctx.getString(com.zhimu.slmp.herd.R.string.herd_unit_day));
            b.herdMetricStage.herdMetricLabel.setText(com.zhimu.slmp.herd.R.string.herd_my_pens_metric_stage);
            b.herdMetricStage.herdMetricValue.setText(single.getStageName() == null ? "" : single.getStageName());
            b.herdMetricStage.herdMetricUnit.setText("");
        }
    }

    private void bindTasks(@NonNull HerdItemMyPenBinding b, @NonNull Context ctx, @NonNull MyPenItemVO pen) {
        b.herdPenTaskProgress.setText(ctx.getString(com.zhimu.slmp.herd.R.string.herd_my_pens_task_progress,
                pen.getTaskDone(), pen.getTaskTotal()));
        b.herdPenTaskBar.setProgressCompat(HerdFormat.progress(pen.getTaskDone(), pen.getTaskTotal()), true);
        b.herdPenTasks.removeAllViews();
        List<TodoBriefVO> tasks = pen.getTasks();
        boolean allDone = tasks.isEmpty() && pen.getTaskTotal() > 0;
        b.herdPenTasksDone.setVisibility(allDone ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (TodoBriefVO task : tasks) {
            HerdItemMyPenTaskBinding tb = HerdItemMyPenTaskBinding.inflate(inflater, b.herdPenTasks, false);
            tb.herdTaskIcon.setImageResource(iconOf(task.getTodoType()));
            StringBuilder title = new StringBuilder(task.getTitle() == null ? "" : task.getTitle());
            if (task.getTimeText() != null) {
                title.append("  ").append(task.getTimeText());
            }
            if (task.getSubtitle() != null) {
                title.append(HerdFormat.DOT).append(task.getSubtitle());
            }
            tb.herdTaskTitle.setText(title);
            tb.herdTaskTag.setText(task.getStatusText() == null ? "待处理" : task.getStatusText());
            ClickThrottle.setOnClickListener(tb.getRoot(), v -> listener.onTaskClick(task, pen));
            b.herdPenTasks.addView(tb.getRoot());
        }
    }

    /** 待办类型（05 号文档 6.2 节 TODO_TYPE）对应的图标 */
    @DrawableRes
    static int iconOf(String todoType) {
        if (todoType == null) {
            return R.drawable.ic_task_alt;
        }
        switch (todoType) {
            case "FEEDING":
                return R.drawable.ic_rice_bowl;
            case "IMMUNIZATION":
                return R.drawable.ic_vaccines;
            case "DISINFECTION":
                return R.drawable.ic_sanitizer;
            case "WEIGHING":
                return R.drawable.ic_scale;
            case "INSPECTION":
                return R.drawable.ic_fact_check;
            default:
                return R.drawable.ic_task_alt;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final HerdItemMyPenBinding binding;

        Holder(@NonNull HerdItemMyPenBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
