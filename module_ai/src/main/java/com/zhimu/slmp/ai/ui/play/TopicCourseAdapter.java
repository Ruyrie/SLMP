package com.zhimu.slmp.ai.ui.play;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiItemTopicCourseBinding;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.util.AiText;

import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * K05 本专题课程列表：正在播放的一行高亮（surface-container-low 底、primary 标题、graphic_eq + “正在播放”、缩略图描边）。
 */
public class TopicCourseAdapter extends ListAdapter<CourseCardVO, TopicCourseAdapter.Holder> {

    private final LongConsumer onClick;
    private long playingId;

    public TopicCourseAdapter(@NonNull LongConsumer onClick) {
        super(new DiffUtil.ItemCallback<CourseCardVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull CourseCardVO a, @NonNull CourseCardVO b) {
                return a.getId() == b.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull CourseCardVO a, @NonNull CourseCardVO b) {
                return Objects.equals(a.getLearnStatus(), b.getLearnStatus()) && a.getPositionSec() == b.getPositionSec()
                        && Objects.equals(a.getProgressRatio(), b.getProgressRatio());
            }
        });
        this.onClick = onClick;
    }

    public void setPlayingId(long id) {
        if (playingId != id) {
            playingId = id;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AiItemTopicCourseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CourseCardVO c = getItem(position);
        boolean playing = c.getId() == playingId;
        h.b.getRoot().setActivated(playing);
        String no = c.getSectionNo() == null ? "" : c.getSectionNo() + ". ";
        h.b.aiTcTitle.setText(no + c.getTitle());
        h.b.aiTcTitle.setTextColor(MaterialColors.getColor(h.b.aiTcTitle, playing
                ? androidx.appcompat.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurface));
        h.b.aiTcPlayingBorder.setVisibility(playing ? View.VISIBLE : View.GONE);
        if (playing) {
            h.b.aiTcSub.setText(R.string.ai_play_now);
            h.b.aiTcSub.setTextColor(MaterialColors.getColor(h.b.aiTcSub, androidx.appcompat.R.attr.colorPrimary));
            Drawable d = AppCompatResources.getDrawable(h.itemView.getContext(), com.zhimu.slmp.base.R.drawable.ic_graphic_eq);
            if (d != null) {
                d = d.mutate();
                int s = Math.round(16 * h.itemView.getResources().getDisplayMetrics().density);
                d.setBounds(0, 0, s, s);
                d.setTint(MaterialColors.getColor(h.b.aiTcSub, androidx.appcompat.R.attr.colorPrimary));
            }
            h.b.aiTcSub.setCompoundDrawablesRelative(d, null, null, null);
        } else {
            h.b.aiTcSub.setText(AiText.topicCourseSub(c));
            h.b.aiTcSub.setTextColor(MaterialColors.getColor(h.b.aiTcSub,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            h.b.aiTcSub.setCompoundDrawablesRelative(null, null, null, null);
        }
        bindProgress(h.b, c);
        Glide.with(h.b.aiTcThumb).load(c.getCoverUrl()).centerCrop().into(h.b.aiTcThumb);
        h.b.getRoot().setOnClickListener(v -> onClick.accept(c.getId()));
    }

    static void bindProgress(@NonNull AiItemTopicCourseBinding b, @NonNull CourseCardVO c) {
        boolean seen = !AiCodes.LEARN_NOT_STARTED.equals(c.getLearnStatus()) && c.getLearnStatus() != null;
        b.aiTcProgress.setVisibility(seen ? View.VISIBLE : View.GONE);
        if (seen) {
            int p = AiCodes.LEARN_COMPLETED.equals(c.getLearnStatus()) ? 100
                    : c.getProgressRatio() == null ? 0 : (int) Math.round(c.getProgressRatio().doubleValue() * 100);
            b.aiTcProgress.setProgressCompat(p, false);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AiItemTopicCourseBinding b;

        Holder(@NonNull AiItemTopicCourseBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
