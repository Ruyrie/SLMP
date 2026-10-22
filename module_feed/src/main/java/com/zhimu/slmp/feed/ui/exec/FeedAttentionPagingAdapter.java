package com.zhimu.slmp.feed.ui.exec;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.base.widget.ListRowView;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.databinding.FeedItemRowBinding;
import com.zhimu.slmp.feed.model.vo.FeedAttentionItemVO;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * G04“需要关注”：投喂异常（warning 图标底 +“投喂异常”）与逾期未登记（error 图标底 +“已逾期”），按日期倒序；
 * 全部批次时主文字前加批次号。有记录或仍可补登的项可点（进入 G02）。
 */
public class FeedAttentionPagingAdapter extends PagingDataAdapter<FeedAttentionItemVO, FeedAttentionPagingAdapter.Holder> {

    private static final DiffUtil.ItemCallback<FeedAttentionItemVO> DIFF = new DiffUtil.ItemCallback<FeedAttentionItemVO>() {
        @Override
        public boolean areItemsTheSame(@NonNull FeedAttentionItemVO a, @NonNull FeedAttentionItemVO b) {
            return a.getTaskId() == b.getTaskId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull FeedAttentionItemVO a, @NonNull FeedAttentionItemVO b) {
            return Objects.equals(a.getRecordId(), b.getRecordId()) && Objects.equals(a.getActualQty(), b.getActualQty())
                    && Objects.equals(a.getTaskStatus(), b.getTaskStatus());
        }
    };

    private final Consumer<FeedAttentionItemVO> onClick;
    private final BooleanSupplier allBatches;

    public FeedAttentionPagingAdapter(@NonNull Consumer<FeedAttentionItemVO> onClick,
                                      @NonNull BooleanSupplier allBatches) {
        super(DIFF);
        this.onClick = onClick;
        this.allBatches = allBatches;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(FeedItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FeedAttentionItemVO item = getItem(position);
        if (item == null) {
            return;
        }
        ListRowView row = holder.binding.feedRow;
        String title = (FeedFormat.date(item.getDate()) + " " + FeedFormat.orEmpty(item.getMealName())).trim();
        if (allBatches.getAsBoolean()) {
            title = FeedFormat.join(item.getBatchNo(), title);
        }
        row.setTitle(title);
        String sub;
        if (item.isOverdueType() || item.getActualQty() == null) {
            sub = FeedFormat.join("计划 " + FeedFormat.kg(item.getPlanQty()), "未登记");
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, ListRowView.TONE_ERROR);
            String text = item.getTaskStatusText() == null ? "已逾期" : item.getTaskStatusText();
            row.setStatusTag(text, StatusTagView.ERROR, com.zhimu.slmp.base.R.drawable.ic_schedule);
        } else {
            sub = FeedFormat.join("实投 " + FeedFormat.kg(item.getActualQty()),
                    "计划 " + FeedFormat.kg(item.getPlanQty()), item.getExecutorName());
            row.setLeadingIcon(com.zhimu.slmp.base.R.drawable.ic_rice_bowl, ListRowView.TONE_WARNING);
            row.setStatusTag("投喂异常", StatusTagView.WARNING, 0);
        }
        row.setSubtitle(sub);
        row.setGroupPosition(GroupPosition.of(position, getItemCount()));
        boolean clickable = item.getRecordId() != null || item.isCanBackfill();
        if (clickable) {
            ClickThrottle.setOnClickListener(row, v -> onClick.accept(item));
        } else {
            row.setOnClickListener(null);
            row.setClickable(false);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FeedItemRowBinding binding;

        Holder(@NonNull FeedItemRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
