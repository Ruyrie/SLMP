package com.zhimu.slmp.ai.ui.course;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.databinding.AiItemCourseCardBinding;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.CourseCardVO;
import com.zhimu.slmp.ai.model.vo.ProgressResultVO;
import com.zhimu.slmp.ai.util.AiText;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * K04 两列视频卡（API-K18 分页）；进度覆盖表变化时只刷新对应卡片。
 */
public class CoursePagingAdapter extends PagingDataAdapter<CourseCardVO, CoursePagingAdapter.Holder> {

    private final Consumer<CourseCardVO> onClick;
    private final LongFunction<ProgressResultVO> overrides;
    private final Function<CourseCardVO, String> subText;

    public CoursePagingAdapter(@NonNull Consumer<CourseCardVO> onClick, @NonNull LongFunction<ProgressResultVO> overrides,
                               @NonNull Function<CourseCardVO, String> subText) {
        super(new DiffUtil.ItemCallback<CourseCardVO>() {
            @Override
            public boolean areItemsTheSame(@NonNull CourseCardVO a, @NonNull CourseCardVO b) {
                return a.getId() == b.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull CourseCardVO a, @NonNull CourseCardVO b) {
                return Objects.equals(a.getLearnStatus(), b.getLearnStatus()) && a.getPositionSec() == b.getPositionSec()
                        && Objects.equals(a.getTitle(), b.getTitle());
            }
        });
        this.onClick = onClick;
        this.overrides = overrides;
        this.subText = subText;
    }

    /** 覆盖表中的课程重新绑定 */
    public void refreshCourse(long courseId) {
        for (int i = 0; i < getItemCount(); i++) {
            CourseCardVO c = peek(i);
            if (c != null && c.getId() == courseId) {
                notifyItemChanged(i);
            }
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AiItemCourseCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CourseCardVO c = getItem(position);
        if (c == null) {
            return;
        }
        h.b.aiCardTitle.setText(c.getTitle());
        h.b.aiCardSub.setText(subText.apply(c));
        h.b.aiCardDuration.setText(AiText.duration(c.getDurationSec()));
        ProgressResultVO r = overrides.apply(c.getId());
        String status = r != null ? r.getLearnStatus() : c.getLearnStatus();
        BigDecimal ratio = r != null ? r.getProgressRatio() : c.getProgressRatio();
        boolean seen = status != null && !AiCodes.LEARN_NOT_STARTED.equals(status);
        h.b.aiCardProgress.setVisibility(seen ? View.VISIBLE : View.GONE);
        if (seen) {
            int p = AiCodes.LEARN_COMPLETED.equals(status) ? 100 : ratio == null ? 0 : Math.round(ratio.floatValue() * 100);
            h.b.aiCardProgress.setProgress(p);
        }
        Glide.with(h.b.aiCardCover).load(c.getCoverUrl()).centerCrop().into(h.b.aiCardCover);
        h.itemView.setOnClickListener(v -> onClick.accept(c));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AiItemCourseCardBinding b;

        Holder(@NonNull AiItemCourseCardBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
