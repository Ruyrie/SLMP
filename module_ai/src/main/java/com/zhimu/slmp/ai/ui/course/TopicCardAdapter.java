package com.zhimu.slmp.ai.ui.course;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.databinding.AiItemTopicCardBinding;
import com.zhimu.slmp.ai.model.vo.TopicVO;
import com.zhimu.slmp.ai.util.AiText;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * K04 专题卡：横向滑动（264 dp，第二张露出一部分）或“全部专题”后纵向撑满。
 */
public class TopicCardAdapter extends ListAdapter<TopicVO, TopicCardAdapter.Holder> {

    private final Consumer<TopicVO> onClick;
    private boolean expanded;

    public TopicCardAdapter(@NonNull Consumer<TopicVO> onClick) {
        super(new DiffUtil.ItemCallback<TopicVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull TopicVO a, @NonNull TopicVO b) {
                return a.getId() == b.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull TopicVO a, @NonNull TopicVO b) {
                return Objects.equals(a.getName(), b.getName())
                        && Objects.equals(a.getCoverUrl(), b.getCoverUrl())
                        && a.getCourseCount() == b.getCourseCount()
                        && a.getTotalDurationSec() == b.getTotalDurationSec()
                        && a.getLearnedCount() == b.getLearnedCount();
            }
        });
        this.onClick = onClick;
    }

    public void setExpanded(boolean e) {
        if (expanded != e) {
            expanded = e;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AiItemTopicCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        TopicVO t = getItem(position);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.b.aiTopicCard.getLayoutParams();
        float d = h.itemView.getResources().getDisplayMetrics().density;
        lp.width = expanded ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(264 * d);
        lp.setMarginEnd(expanded ? 0 : Math.round(12 * d));
        h.b.aiTopicCard.setLayoutParams(lp);
        h.b.aiTopicName.setText(t.getName());
        h.b.aiTopicMeta.setText(AiText.topicMeta(t));
        h.b.aiTopicLearned.setText(AiText.learned(t.getLearnedCount(), t.getCourseCount()));
        int p = t.getCourseCount() == 0 ? 0 : Math.round(t.getLearnedCount() * 100f / t.getCourseCount());
        h.b.aiTopicProgress.setProgressCompat(p, false);
        Glide.with(h.b.aiTopicCover).load(t.getCoverUrl()).centerCrop().into(h.b.aiTopicCover);
        h.b.aiTopicCard.setOnClickListener(v -> onClick.accept(t));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AiItemTopicCardBinding b;

        Holder(@NonNull AiItemTopicCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
