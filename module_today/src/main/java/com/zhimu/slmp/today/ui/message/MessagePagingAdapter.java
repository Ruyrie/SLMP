package com.zhimu.slmp.today.ui.message;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.GroupPosition;
import com.zhimu.slmp.today.R;
import com.zhimu.slmp.today.databinding.TodayItemMessageBinding;
import com.zhimu.slmp.today.databinding.TodayItemMessageHeaderBinding;
import com.zhimu.slmp.today.model.vo.MessageActionVO;
import com.zhimu.slmp.today.model.vo.MessageBriefVO;
import com.zhimu.slmp.today.ui.common.TodoPagingAdapter;
import com.zhimu.slmp.today.util.MessageRow;
import com.zhimu.slmp.today.util.TodayBindingAdapters;
import com.zhimu.slmp.today.util.TodoTypeStyle;
import com.zhimu.slmp.today.util.Texts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 消息分页列表：日期分组标题 + 消息项（分组圆角按前后是否为标题计算）。未读圆点、邀请“拒绝 / 同意”、长按菜单由页面处理。
 */
public class MessagePagingAdapter extends PagingDataAdapter<MessageRow, RecyclerView.ViewHolder> {

    /** 点击回调 */
    public interface Listener {
        void onMessageClick(@NonNull MessageBriefVO m);

        void onMessageLongClick(@NonNull View anchor, @NonNull MessageBriefVO m);

        void onAccept(@NonNull MessageBriefVO m);

        void onReject(@NonNull MessageBriefVO m);
    }

    private static final DiffUtil.ItemCallback<MessageRow> DIFF = new DiffUtil.ItemCallback<MessageRow>() {
        @Override
        public boolean areItemsTheSame(@NonNull MessageRow a, @NonNull MessageRow b) {
            return a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MessageRow a, @NonNull MessageRow b) {
            return a.equals(b);
        }
    };

    @NonNull
    private final Listener listener;
    @Nullable
    private Long actingActionId;

    public MessagePagingAdapter(@NonNull Listener listener) {
        super(DIFF);
        this.listener = listener;
        registerAdapterDataObserver(new TodoPagingAdapter.NeighborRebinder(this, new Handler(Looper.getMainLooper())));
    }

    /** 正在处理的邀请（按钮禁用、“同意”加载） */
    public void setActingActionId(@Nullable Long id) {
        if (!Objects.equals(actingActionId, id)) {
            actingActionId = id;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override
    public int getItemViewType(int position) {
        MessageRow row = peek(position);
        return row != null && row.isHeader() ? MessageRow.TYPE_HEADER : MessageRow.TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == MessageRow.TYPE_HEADER) {
            return new HeaderVH(TodayItemMessageHeaderBinding.inflate(inflater, parent, false));
        }
        return new ItemVH(TodayItemMessageBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageRow row = getItem(position);
        if (row == null) {
            return;
        }
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).b.title.setText(MessageRow.groupTitle(row.getHeader()));
            return;
        }
        MessageBriefVO m = row.getMessage();
        if (!(holder instanceof ItemVH) || m == null) {
            return;
        }
        TodayItemMessageBinding b = ((ItemVH) holder).b;
        b.unread.setVisibility(row.isRead() ? View.INVISIBLE : View.VISIBLE);
        b.icon.setImageResource(TodoTypeStyle.messageIconOf(m));
        TodayBindingAdapters.applyTone(b.icon, TodoTypeStyle.messageToneOf(m, row.getActionStatus()));
        b.title.setText(m.getTitle());

        boolean farm = m.getFarm() != null && !Texts.isEmpty(m.getFarm().getName());
        b.farmTag.setVisibility(farm ? View.VISIBLE : View.GONE);
        if (farm) {
            b.farmTag.setText(m.getFarm().getName());
        }
        String sub = subtitleOf(b.getRoot(), m, row);
        b.subtitle.setText(sub);
        b.time.setText(timeOf(m));

        boolean showTag = !Texts.isEmpty(m.getTagText()) && !m.isRevoked();
        b.tag.setVisibility(showTag ? View.VISIBLE : View.GONE);
        if (showTag) {
            b.tag.setStatus(m.getTagText(), TodoTypeStyle.messageTagTypeOf(m.getTagCode()));
            b.tag.setTagIcon(TodoTypeStyle.messageTagIconOf(m.getTagCode()));
        }

        boolean pending = row.isActionPending();
        b.inviteActions.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            MessageActionVO action = m.getAction();
            boolean acting = action != null && Objects.equals(actingActionId, action.getActionId());
            ButtonLoading.setLoading(b.accept, acting);
            b.reject.setEnabled(!acting && actingActionId == null);
            b.accept.setEnabled(actingActionId == null || acting);
            ClickThrottle.setOnClickListener(b.accept, v -> listener.onAccept(m));
            ClickThrottle.setOnClickListener(b.reject, v -> listener.onReject(m));
        }

        MessageRow prev = position > 0 ? peek(position - 1) : null;
        MessageRow next = position + 1 < getItemCount() ? peek(position + 1) : null;
        boolean top = prev == null || prev.isHeader();
        boolean bottom = next == null || next.isHeader();
        int gp = top && bottom ? GroupPosition.SINGLE : top ? GroupPosition.TOP : bottom ? GroupPosition.BOTTOM
                : GroupPosition.MIDDLE;
        TodayBindingAdapters.applyGroupPosition(b.card, gp, true);

        ClickThrottle.setOnClickListener(b.card, v -> listener.onMessageClick(m));
        b.card.setOnLongClickListener(v -> {
            listener.onMessageLongClick(v, m);
            return true;
        });
        b.card.setContentDescription(Texts.join("，", java.util.Arrays.asList(
                row.isRead() ? null : b.getRoot().getContext().getString(com.zhimu.slmp.base.R.string.base_cd_unread),
                m.getTitle(), farm ? m.getFarm().getName() : null, sub, b.time.getText().toString(),
                showTag ? m.getTagText() : null)));
    }

    /** 副文字：邀请写“员工 · 7 天内有效 / 已同意 / 已拒绝 / 已过期”；已撤回的通知写“已撤回” */
    @Nullable
    static String subtitleOf(@NonNull View v, @NonNull MessageBriefVO m, @NonNull MessageRow row) {
        if (m.isRevoked()) {
            return v.getContext().getString(R.string.today_message_revoked);
        }
        MessageActionVO action = m.getAction();
        if (action != null && m.isInvite()) {
            String status = row.getActionStatus();
            String text;
            if (MessageRow.STATUS_ACCEPTED.equals(status)) {
                text = v.getContext().getString(R.string.today_message_status_accepted);
            } else if (MessageRow.STATUS_REJECTED.equals(status)) {
                text = v.getContext().getString(R.string.today_message_status_rejected);
            } else if (MessageRow.STATUS_EXPIRED.equals(status)) {
                text = v.getContext().getString(R.string.today_message_status_expired);
            } else {
                text = action.getStatusText();
            }
            return Texts.join(" · ", java.util.Arrays.asList(action.getRoleText(), text));
        }
        return m.getSummary();
    }

    /** 今天和昨天写“09:21”，更早写“5月6日”，跨年写“2025年12月3日” */
    @NonNull
    static String timeOf(@NonNull MessageBriefVO m) {
        String t = m.getSortTime() != null ? m.getSortTime() : m.getCreatedAt();
        String group = m.getDateGroup();
        if ("TODAY".equals(group) || "YESTERDAY".equals(group)) {
            return DateFormatter.formatTime(t);
        }
        LocalDateTime dt = DateFormatter.parseDateTime(t);
        LocalDate d = dt == null ? DateFormatter.parseDate(t) : dt.toLocalDate();
        return d == null ? "" : DateFormatter.monthDay(d);
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TodayItemMessageHeaderBinding b;

        HeaderVH(@NonNull TodayItemMessageHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class ItemVH extends RecyclerView.ViewHolder {
        final TodayItemMessageBinding b;

        ItemVH(@NonNull TodayItemMessageBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
