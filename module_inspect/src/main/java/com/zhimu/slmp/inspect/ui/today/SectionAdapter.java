package com.zhimu.slmp.inspect.ui.today;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.inspect.databinding.InspectItemSectionTitleBinding;
import com.zhimu.slmp.inspect.databinding.InspectItemTodayRowBinding;
import com.zhimu.slmp.inspect.model.vo.InspectionListItemVO;
import com.zhimu.slmp.inspect.model.vo.RecentIssueVO;
import com.zhimu.slmp.inspect.ui.common.RowStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * I01 清单下方的分组：养殖户版“前 3 天 · 超时与补录”（超时、补录、可补录的漏巡），员工版“昨天 · 10月18日”。
 * 第 0 项为分组标题。
 */
final class SectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Callback {
        void onIssueClick(@NonNull RecentIssueVO issue);

        void onBackfillClick(@NonNull RecentIssueVO issue);

        void onYesterdayClick(@NonNull InspectionListItemVO item);
    }

    private static final int TYPE_TITLE = 0;
    private static final int TYPE_ROW = 1;

    private final Callback callback;
    @Nullable
    private String title;
    private List<RecentIssueVO> issues = Collections.emptyList();
    private List<InspectionListItemVO> yesterday = Collections.emptyList();

    SectionAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    void showIssues(@NonNull String sectionTitle, @Nullable List<RecentIssueVO> list) {
        title = sectionTitle;
        issues = list == null ? Collections.emptyList() : new ArrayList<>(list);
        yesterday = Collections.emptyList();
        notifyDataSetChanged();
    }

    void showYesterday(@NonNull String sectionTitle, @Nullable List<InspectionListItemVO> list) {
        title = sectionTitle;
        yesterday = list == null ? Collections.emptyList() : new ArrayList<>(list);
        issues = Collections.emptyList();
        notifyDataSetChanged();
    }

    void hide() {
        title = null;
        issues = Collections.emptyList();
        yesterday = Collections.emptyList();
        notifyDataSetChanged();
    }

    private int rows() {
        return issues.isEmpty() ? yesterday.size() : issues.size();
    }

    @Override
    public int getItemCount() {
        return title == null || rows() == 0 ? 0 : rows() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_TITLE : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TITLE) {
            InspectItemSectionTitleBinding b = InspectItemSectionTitleBinding.inflate(inflater, parent, false);
            return new RecyclerView.ViewHolder(b.getRoot()) {
            };
        }
        return new TodayRowHolder(InspectItemTodayRowBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position == 0) {
            ((android.widget.TextView) holder.itemView.findViewById(
                    com.zhimu.slmp.inspect.R.id.inspect_section_title)).setText(title);
            return;
        }
        TodayRowHolder h = (TodayRowHolder) holder;
        int index = position - 1;
        RowStyle.position(h.b.inspectRowCard, index, rows());
        h.b.inspectRowActions.setVisibility(View.GONE);
        if (!issues.isEmpty()) {
            RecentIssueVO issue = issues.get(index);
            h.b.inspectRowTitle.setText(TodayTexts.issueTitle(issue));
            h.b.inspectRowSubtitle.setText(TodayTexts.issueSubtitle(issue));
            h.b.inspectRowMore.setVisibility(View.GONE);
            h.b.inspectRowTags.setVisibility(View.GONE);
            if (RecentIssueVO.TYPE_MISSED.equals(issue.getIssueType())) {
                RowStyle.leading(h.b.inspectRowLeading, com.zhimu.slmp.base.R.drawable.ic_fact_check,
                        RowStyle.TONE_SECONDARY);
                h.b.inspectRowTag.setVisibility(View.GONE);
                h.b.inspectRowButton.setVisibility(issue.isCanBackfill() ? View.VISIBLE : View.GONE);
                ClickThrottle.setOnClickListener(h.b.inspectRowButton, v -> callback.onBackfillClick(issue));
            } else {
                RowStyle.leading(h.b.inspectRowLeading, com.zhimu.slmp.base.R.drawable.ic_fact_check,
                        RowStyle.TONE_NEUTRAL);
                h.b.inspectRowButton.setVisibility(View.GONE);
                boolean overdue = RecentIssueVO.TYPE_OVERDUE.equals(issue.getIssueType());
                RowStyle.tag(h.b.inspectRowTag, overdue ? "超时" : "补录", StatusTagView.NEUTRAL, overdue
                        ? com.zhimu.slmp.base.R.drawable.ic_schedule : com.zhimu.slmp.base.R.drawable.ic_history);
            }
            ClickThrottle.setOnClickListener(h.b.getRoot(), v -> callback.onIssueClick(issue));
        } else {
            InspectionListItemVO item = yesterday.get(index);
            h.bindStatus(item, true);
            h.b.inspectRowTitle.setText(item.getPenName());
            h.b.inspectRowSubtitle.setText(TodayTexts.staffSubtitle(item, null).replaceFirst("^[^\\n]*\\n", ""));
            ClickThrottle.setOnClickListener(h.b.getRoot(), v -> callback.onYesterdayClick(item));
        }
    }
}
