package com.zhimu.slmp.ai.ui.session;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiItemGroupTitleBinding;
import com.zhimu.slmp.ai.databinding.AiItemSessionBinding;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.AiSessionItemVO;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.widget.GroupPosition;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * K02 会话列表：分组标题 + 分组列表项（首末项外侧圆角 20 dp，按前后是否为同组判断）。
 */
public class AiSessionPagingAdapter extends PagingDataAdapter<SessionRow, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Consumer<AiSessionItemVO> onClick;
    /** 长按：弹出“重命名 / 删除”菜单（锚点为卡片） */
    @Nullable
    private final BiConsumer<View, AiSessionItemVO> onLongClick;

    public AiSessionPagingAdapter(@NonNull Consumer<AiSessionItemVO> onClick) {
        this(onClick, null);
    }

    public AiSessionPagingAdapter(@NonNull Consumer<AiSessionItemVO> onClick,
                                  @Nullable BiConsumer<View, AiSessionItemVO> onLongClick) {
        super(new DiffUtil.ItemCallback<SessionRow>() {
            @Override
            public boolean areItemsTheSame(@NonNull SessionRow a, @NonNull SessionRow b) {
                return a.key().equals(b.key());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SessionRow a, @NonNull SessionRow b) {
                return a.equals(b);
            }
        });
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }

    @Override
    public int getItemViewType(int position) {
        SessionRow r = peek(position);
        return r != null && r.isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(AiItemGroupTitleBinding.inflate(inf, parent, false));
        }
        return new ItemHolder(AiItemSessionBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SessionRow row = getItem(position);
        if (row == null) {
            return;
        }
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).b.aiGroupTitle.setText(row.header);
            return;
        }
        ItemHolder h = (ItemHolder) holder;
        AiSessionItemVO s = row.item;
        if (s == null) {
            return;
        }
        h.b.aiSessionTitle.setText(s.getTitle());
        h.b.aiSessionSub.setText(AiText.sessionSub(s));
        h.b.aiSessionIcon.setImageResource(iconOf(s.getTopic()));
        h.b.aiSessionTagCorrection.setVisibility(s.isHasCorrection() ? View.VISIBLE : View.GONE);
        h.b.aiSessionTagTruncated.setVisibility(s.isContextTruncated() ? View.VISIBLE : View.GONE);
        h.b.aiSessionTags.setVisibility(s.isHasCorrection() || s.isContextTruncated() ? View.VISIBLE : View.GONE);
        boolean first = !sameGroup(peekSafe(position - 1), row);
        boolean last = !sameGroup(peekSafe(position + 1), row);
        int pos = first && last ? GroupPosition.SINGLE : first ? GroupPosition.TOP
                : last ? GroupPosition.BOTTOM : GroupPosition.MIDDLE;
        GroupPosition.apply(h.b.aiSessionCard, pos);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.b.aiSessionCard.getLayoutParams();
        lp.bottomMargin = last ? 0 : Math.round(2 * h.itemView.getResources().getDisplayMetrics().density);
        h.b.aiSessionCard.setLayoutParams(lp);
        h.b.aiSessionCard.setOnClickListener(v -> onClick.accept(s));
        if (onLongClick != null) {
            h.b.aiSessionCard.setOnLongClickListener(v -> {
                onLongClick.accept(v, s);
                return true;
            });
        }
    }

    @Nullable
    private SessionRow peekSafe(int position) {
        return position < 0 || position >= getItemCount() ? null : peek(position);
    }

    private static boolean sameGroup(@Nullable SessionRow other, @NonNull SessionRow row) {
        return other != null && !other.isHeader() && java.util.Objects.equals(other.group(), row.group());
    }

    @DrawableRes
    static int iconOf(@Nullable String topic) {
        if (AiCodes.TOPIC_MEDICATION.equals(topic)) {
            return com.zhimu.slmp.base.R.drawable.ic_medication;
        }
        if (AiCodes.TOPIC_FEEDING.equals(topic)) {
            return com.zhimu.slmp.base.R.drawable.ic_rice_bowl;
        }
        if (AiCodes.TOPIC_DISEASE.equals(topic)) {
            return R.drawable.ai_ic_healing;
        }
        if (AiCodes.TOPIC_IMMUNE.equals(topic)) {
            return com.zhimu.slmp.base.R.drawable.ic_vaccines;
        }
        if (AiCodes.TOPIC_DISINFECT.equals(topic)) {
            return com.zhimu.slmp.base.R.drawable.ic_sanitizer;
        }
        return R.drawable.ai_ic_question_answer;
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final AiItemGroupTitleBinding b;

        HeaderHolder(@NonNull AiItemGroupTitleBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class ItemHolder extends RecyclerView.ViewHolder {
        final AiItemSessionBinding b;

        ItemHolder(@NonNull AiItemSessionBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
