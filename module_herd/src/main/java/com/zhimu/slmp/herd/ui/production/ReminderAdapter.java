package com.zhimu.slmp.herd.ui.production;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.herd.model.vo.ProductionReminderItemVO;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 生产提醒页列表（{@link ProductionReminderActivity}）：40 dp warning-container 图标块 + 标题 + 次行 + warning 标签。
 */
public class ReminderAdapter extends ListAdapter<ProductionReminderItemVO, ReminderAdapter.Holder> {

    private final Consumer<ProductionReminderItemVO> onClick;

    public ReminderAdapter(@NonNull Consumer<ProductionReminderItemVO> onClick) {
        super(new DiffUtil.ItemCallback<ProductionReminderItemVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull ProductionReminderItemVO a, @NonNull ProductionReminderItemVO b) {
                return Objects.equals(a.getReminderType(), b.getReminderType())
                        && Objects.equals(a.getReportId(), b.getReportId())
                        && Objects.equals(a.getBatchId(), b.getBatchId())
                        && Objects.equals(a.getTodoId(), b.getTodoId())
                        && Objects.equals(a.getTitle(), b.getTitle());
            }

            @Override
            public boolean areContentsTheSame(@NonNull ProductionReminderItemVO a,
                                              @NonNull ProductionReminderItemVO b) {
                return Objects.equals(a.getSubtitle(), b.getSubtitle())
                        && Objects.equals(a.getTagText(), b.getTagText());
            }
        });
        this.onClick = onClick;
    }

    /** 提醒类型对应的图标 */
    static int iconOf(@NonNull ProductionReminderItemVO item) {
        String t = item.getReminderType();
        if ("WITHDRAWAL".equals(t)) {
            return R.drawable.ic_medication;
        }
        if ("FEED_LOW_STOCK".equals(t)) {
            return R.drawable.ic_inventory_2;
        }
        if ("DRUG_LOW_STOCK".equals(t) || "DRUG_NEAR_EXPIRY".equals(t) || "DRUG_EXPIRED".equals(t)) {
            return R.drawable.ic_medical_services;
        }
        if ("URGENT_DIAGNOSIS".equals(t)) {
            return R.drawable.ic_emergency;
        }
        if (ProductionReminderItemVO.TYPE_ABNORMAL_REPORT.equals(t) || "INSPECT_ABNORMAL".equals(t)) {
            return R.drawable.ic_report;
        }
        return R.drawable.ic_warning;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ListRowView row = new ListRowView(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ProductionReminderItemVO item = getItem(position);
        ListRowView row = holder.row;
        row.setGroupPosition(GroupPosition.of(position, getItemCount()));
        row.setLeadingIcon(iconOf(item), ListRowView.TONE_WARNING);
        row.setTitle(item.getTitle());
        row.setSubtitle(item.getSubtitle());
        row.setStatusTag(item.getTagText(), item.isWarning() ? StatusTagView.WARNING : StatusTagView.PRIMARY, 0);
        ClickThrottle.setOnClickListener(row, v -> onClick.accept(item));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ListRowView row;

        Holder(@NonNull ListRowView row) {
            super(row);
            this.row = row;
        }
    }
}
