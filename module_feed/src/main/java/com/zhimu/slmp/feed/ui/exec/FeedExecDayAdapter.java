package com.zhimu.slmp.feed.ui.exec;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.databinding.FeedItemRowBinding;
import com.zhimu.slmp.feed.model.vo.FeedExecDayVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.ArrayList;
import java.util.List;

/** G04 点柱后的当天各餐明细（API-G16），显示在“需要关注”的位置。 */
public class FeedExecDayAdapter extends RecyclerView.Adapter<FeedExecDayAdapter.Holder> {

    private final List<FeedExecDayVO.FeedExecDayItemVO> items = new ArrayList<>();
    private boolean allBatches;

    public void submit(@Nullable FeedExecDayVO day, boolean allBatches) {
        this.allBatches = allBatches;
        int old = items.size();
        items.clear();
        notifyItemRangeRemoved(0, old);
        if (day != null) {
            items.addAll(day.getItems());
        }
        notifyItemRangeInserted(0, items.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FeedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FeedExecDayVO.FeedExecDayItemVO it = items.get(position);
        ListRowView row = holder.binding.feedRow;
        String title = FeedFormat.join(allBatches ? it.getBatchNo() : null, it.getPenName(), it.getMealName());
        row.setTitle(title);
        boolean done = "DONE".equals(it.getStatus()) || "MANUAL_DONE".equals(it.getStatus());
        boolean abnormal = done && it.getAbnormalType() != null && !"NONE".equals(it.getAbnormalType());
        if (done && it.getActualQty() != null) {
            row.setSubtitle(FeedFormat.join("实投 " + FeedFormat.kg(it.getActualQty()),
                    "计划 " + FeedFormat.kg(it.getPlanQty()), it.getExecutorName()));
        } else {
            row.setSubtitle(FeedFormat.join("计划 " + FeedFormat.kg(it.getPlanQty()), done ? it.getStatusText()
                    : "未登记"));
        }
        if (abnormal) {
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, ListRowView.TONE_WARNING);
            row.setStatusTag("投喂异常", StatusTagView.WARNING, 0);
        } else if (done) {
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, ListRowView.TONE_NEUTRAL);
            row.setStatusTag(it.getStatusText() == null ? "已投喂" : it.getStatusText(), StatusTagView.NEUTRAL,
                    com.zhimu.slmp.base.R.drawable.ic_check);
        } else {
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, ListRowView.TONE_ERROR);
            row.setStatusTag(it.getStatusText() == null ? "已逾期" : it.getStatusText(), StatusTagView.ERROR,
                    com.zhimu.slmp.base.R.drawable.ic_schedule);
        }
        row.setGroupPosition(GroupPosition.of(position, items.size()));
        row.setOnClickListener(null);
        row.setClickable(false);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FeedItemRowBinding binding;

        Holder(@NonNull FeedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
