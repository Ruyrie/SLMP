package com.zhimu.slmp.ai.ui.chat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiItemChatAnswerBinding;
import com.zhimu.slmp.ai.databinding.AiItemChatCenterBinding;
import com.zhimu.slmp.ai.databinding.AiItemChatQuestionBinding;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;
import com.zhimu.slmp.ai.util.AnswerRenderer;
import com.zhimu.slmp.common.model.vo.FileVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * K01 对话列表（多类型）：时间分隔 / 系统提示 / 10 轮分隔、提问气泡（附图缩略图）、AI 回答。
 * 生成中的正文不经过 submitList：{@link #setStreaming} 按轮次定位，notifyItemChanged 带 {@link #PAYLOAD_TEXT} 只重设正文。
 */
public class AiMessageAdapter extends ListAdapter<ChatItem, RecyclerView.ViewHolder> {

    public interface Listener {
        void onCitation(@NonNull CitationBriefVO citation);

        void onRegenerate(long roundId);

        void onRetrySend(long roundId);

        void onRate(long roundId, @NonNull String rating);

        void onCopy(long roundId);

        void onImageClick(@NonNull ChatItem item, int index);
    }

    static final Object PAYLOAD_TEXT = new Object();
    private static final int TYPE_CENTER = 0;
    private static final int TYPE_QUESTION = 1;
    private static final int TYPE_ANSWER = 2;
    private static final long CURSOR_BLINK_MS = 500;

    private final Listener listener;
    @Nullable
    private Long streamingRoundId;
    @NonNull
    private CharSequence streamingText = "";

    public AiMessageAdapter(@NonNull Listener listener) {
        super(new DiffUtil.ItemCallback<ChatItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull ChatItem a, @NonNull ChatItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull ChatItem a, @NonNull ChatItem b) {
                return a.sameContent(b);
            }
        });
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    /** 生成中的正文（已去掉 [^n]）；roundId 为空表示没有在生成 */
    public void setStreaming(@Nullable Long roundId, @NonNull CharSequence text) {
        streamingRoundId = roundId;
        streamingText = text;
        if (roundId == null) {
            return;
        }
        int pos = positionOfAnswer(roundId);
        if (pos >= 0) {
            notifyItemChanged(pos, PAYLOAD_TEXT);
        }
    }

    public int positionOfAnswer(long roundId) {
        List<ChatItem> list = getCurrentList();
        for (int i = list.size() - 1; i >= 0; i--) {
            ChatItem it = list.get(i);
            if (it.type == ChatItem.Type.ANSWER && it.roundId == roundId) {
                return i;
            }
        }
        return -1;
    }

    /** 按 id 找位置（加载更早后恢复锚点） */
    public int positionOfId(long id) {
        List<ChatItem> list = getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem it = getItem(position);
        if (it.type == ChatItem.Type.QUESTION) {
            return TYPE_QUESTION;
        }
        if (it.type == ChatItem.Type.ANSWER) {
            return TYPE_ANSWER;
        }
        return TYPE_CENTER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_QUESTION) {
            return new QuestionHolder(AiItemChatQuestionBinding.inflate(inf, parent, false));
        }
        if (viewType == TYPE_ANSWER) {
            return new AnswerHolder(AiItemChatAnswerBinding.inflate(inf, parent, false));
        }
        return new CenterHolder(AiItemChatCenterBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem it = getItem(position);
        if (holder instanceof QuestionHolder) {
            ((QuestionHolder) holder).bind(it);
        } else if (holder instanceof AnswerHolder) {
            ((AnswerHolder) holder).bind(it);
        } else {
            ((CenterHolder) holder).bind(it);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_TEXT) && holder instanceof AnswerHolder) {
            ((AnswerHolder) holder).bindStreamingText(getItem(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AnswerHolder) {
            ((AnswerHolder) holder).stopCursor();
        }
        super.onViewRecycled(holder);
    }

    // ===== ViewHolder =====

    static final class CenterHolder extends RecyclerView.ViewHolder {
        private final AiItemChatCenterBinding b;

        CenterHolder(@NonNull AiItemChatCenterBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(@NonNull ChatItem it) {
            b.aiCenterText.setText(it.text);
            boolean divider = it.type == ChatItem.Type.DIVIDER;
            b.aiCenterLineStart.setVisibility(divider ? View.VISIBLE : View.GONE);
            b.aiCenterLineEnd.setVisibility(divider ? View.VISIBLE : View.GONE);
        }
    }

    final class QuestionHolder extends RecyclerView.ViewHolder {
        private final AiItemChatQuestionBinding b;

        QuestionHolder(@NonNull AiItemChatQuestionBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(@NonNull ChatItem it) {
            String text = it.text == null ? "" : it.text;
            b.aiQuestionText.setText(text);
            b.aiQuestionText.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            ThumbFlowLayout row = b.aiQuestionImages;
            row.removeAllViews();
            List<String> urls = new ArrayList<>(it.localImages);
            for (FileVO f : it.remoteImages) {
                urls.add(f.getThumbUrl() != null ? f.getThumbUrl() : f.getUrl());
            }
            row.setVisibility(urls.isEmpty() ? View.GONE : View.VISIBLE);
            Context c = row.getContext();
            int size = Math.round(64 * c.getResources().getDisplayMetrics().density);
            float radius = 12 * c.getResources().getDisplayMetrics().density;
            for (int i = 0; i < urls.size(); i++) {
                ShapeableImageView iv = new ShapeableImageView(c);
                iv.setLayoutParams(new ViewGroup.LayoutParams(size, size));
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(radius).build());
                iv.setBackgroundResource(R.drawable.ai_bg_cover);
                final int index = i;
                iv.setOnClickListener(v -> listener.onImageClick(it, index));
                Glide.with(iv).load(urls.get(i)).centerCrop().into(iv);
                row.addView(iv);
            }
        }
    }

    final class AnswerHolder extends RecyclerView.ViewHolder {
        private final AiItemChatAnswerBinding b;
        private final Handler handler = new Handler(Looper.getMainLooper());
        @Nullable
        private AnswerRenderer.CursorSpan cursor;
        private final Runnable blink = new Runnable() {
            @Override
            public void run() {
                if (cursor == null) {
                    return;
                }
                cursor.setVisible(!cursor.isVisible());
                b.aiAnswerText.invalidate();
                handler.postDelayed(this, CURSOR_BLINK_MS);
            }
        };

        AnswerHolder(@NonNull AiItemChatAnswerBinding b) {
            super(b.getRoot());
            this.b = b;
            b.aiAnswerText.setMovementMethod(LinkMovementMethod.getInstance());
        }

        void bind(@NonNull ChatItem it) {
            stopCursor();
            ChatItem.AnswerState st = it.state == null ? ChatItem.AnswerState.THINKING : it.state;
            boolean failedSend = st == ChatItem.AnswerState.SEND_FAILED;
            b.aiAnswerAvatar.setVisibility(failedSend ? View.INVISIBLE : View.VISIBLE);
            b.aiAnswerThinking.setVisibility(st == ChatItem.AnswerState.THINKING ? View.VISIBLE : View.GONE);
            AiMessageVO m = it.message;
            boolean done = st == ChatItem.AnswerState.DONE && m != null;
            b.aiAnswerBanned.setVisibility(done && AiCodes.ANSWER_BANNED_DRUG.equals(m.getAnswerType())
                    ? View.VISIBLE : View.GONE);
            // 正文
            if (done) {
                b.aiAnswerText.setText(AnswerRenderer.render(b.getRoot().getContext(), m, listener::onCitation));
                b.aiAnswerText.setVisibility(View.VISIBLE);
            } else if (st == ChatItem.AnswerState.STREAMING) {
                bindStreamingText(it);
            } else if (it.partialText != null && !it.partialText.trim().isEmpty()) {
                b.aiAnswerText.setText(it.partialText);
                b.aiAnswerText.setVisibility(View.VISIBLE);
            } else {
                b.aiAnswerText.setVisibility(View.GONE);
            }
            bindSources(done ? m : null);
            bindStatus(it, st);
            bindFooter(it, st, m);
            bindCorrection(done ? m : null);
            b.getRoot().setAlpha(1f);
        }

        void bindStreamingText(@NonNull ChatItem it) {
            if (!Objects.equals(streamingRoundId, it.roundId) || it.state != ChatItem.AnswerState.STREAMING) {
                return;
            }
            if (cursor == null) {
                cursor = new AnswerRenderer.CursorSpan(b.getRoot().getContext());
                handler.postDelayed(blink, CURSOR_BLINK_MS);
            }
            cursor.setVisible(true);
            b.aiAnswerText.setText(AnswerRenderer.streaming(streamingText, cursor));
            b.aiAnswerText.setVisibility(View.VISIBLE);
            b.aiAnswerThinking.setVisibility(View.GONE);
        }

        void stopCursor() {
            handler.removeCallbacks(blink);
            cursor = null;
        }

        private void bindSources(@Nullable AiMessageVO m) {
            b.aiAnswerSources.removeAllViews();
            List<CitationBriefVO> list = m == null ? Collections.emptyList() : new ArrayList<>(m.getCitations());
            if (list.isEmpty()) {
                b.aiAnswerSources.setVisibility(View.GONE);
                return;
            }
            list.sort(Comparator.comparingInt(CitationBriefVO::getSeqNo));
            Context c = b.getRoot().getContext();
            float d = c.getResources().getDisplayMetrics().density;
            for (CitationBriefVO cite : list) {
                TextView chip = new TextView(c);
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(32 * d));
                lp.topMargin = Math.round(8 * d);
                chip.setLayoutParams(lp);
                chip.setBackgroundResource(R.drawable.ai_bg_outline_8);
                chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
                chip.setPadding(Math.round(8 * d), 0, Math.round(12 * d), 0);
                chip.setMaxLines(1);
                chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                chip.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_LabelLarge);
                chip.setTextColor(com.google.android.material.color.MaterialColors.getColor(chip,
                        com.google.android.material.R.attr.colorOnSurface));
                chip.setText(cite.getSeqNo() + " " + cite.getTitle());
                int icon = AiCodes.ICON_ARTICLE.equals(cite.getIconType())
                        ? com.zhimu.slmp.base.R.drawable.ic_description : com.zhimu.slmp.base.R.drawable.ic_menu_book;
                android.graphics.drawable.Drawable dr = androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(c, icon);
                if (dr != null) {
                    dr = dr.mutate();
                    int s = Math.round(18 * d);
                    dr.setBounds(0, 0, s, s);
                    dr.setTint(com.google.android.material.color.MaterialColors.getColor(chip,
                            androidx.appcompat.R.attr.colorPrimary));
                    chip.setCompoundDrawablesRelative(dr, null, null, null);
                    chip.setCompoundDrawablePadding(Math.round(6 * d));
                }
                chip.setOnClickListener(v -> listener.onCitation(cite));
                b.aiAnswerSources.addView(chip);
            }
            b.aiAnswerSources.setVisibility(View.VISIBLE);
        }

        private void bindStatus(@NonNull ChatItem it, @NonNull ChatItem.AnswerState st) {
            boolean show = st == ChatItem.AnswerState.STOPPED || st == ChatItem.AnswerState.ERROR
                    || st == ChatItem.AnswerState.SEND_FAILED;
            b.aiAnswerStatus.setVisibility(show ? View.VISIBLE : View.GONE);
            b.aiAnswerRegenerate.setVisibility(View.GONE);
            b.aiAnswerRetry.setVisibility(View.GONE);
            b.aiAnswerStatus.setOnClickListener(null);
            b.aiAnswerStatus.setClickable(false);
            if (!show) {
                return;
            }
            b.aiAnswerStatusText.setText(it.statusText);
            Context c = b.getRoot().getContext();
            int error = com.google.android.material.color.MaterialColors.getColor(b.aiAnswerStatusText,
                    androidx.appcompat.R.attr.colorError);
            int variant = com.google.android.material.color.MaterialColors.getColor(b.aiAnswerStatusText,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            float alpha = it.actionsEnabled ? 1f : 0.38f;
            if (st == ChatItem.AnswerState.STOPPED) {
                b.aiAnswerStatusIcon.setVisibility(View.GONE);
                b.aiAnswerStatusText.setTextColor(variant);
                b.aiAnswerRegenerate.setVisibility(View.VISIBLE);
                b.aiAnswerRegenerate.setAlpha(alpha);
                b.aiAnswerRegenerate.setOnClickListener(v -> {
                    if (it.actionsEnabled) {
                        listener.onRegenerate(it.roundId);
                    }
                });
            } else if (st == ChatItem.AnswerState.SEND_FAILED) {
                b.aiAnswerStatusIcon.setVisibility(View.VISIBLE);
                b.aiAnswerStatusText.setTextColor(error);
                b.aiAnswerRetry.setVisibility(View.VISIBLE);
                b.aiAnswerRetry.setAlpha(alpha);
                b.aiAnswerRetry.setOnClickListener(v -> {
                    if (it.actionsEnabled) {
                        listener.onRetrySend(it.roundId);
                    }
                });
            } else {
                b.aiAnswerStatusIcon.setVisibility(View.VISIBLE);
                b.aiAnswerStatusText.setTextColor(error);
                b.aiAnswerStatus.setAlpha(alpha);
                b.aiAnswerStatus.setClickable(true);
                b.aiAnswerStatus.setOnClickListener(v -> {
                    if (it.actionsEnabled) {
                        listener.onRegenerate(it.roundId);
                    }
                });
                b.aiAnswerStatus.setContentDescription(c.getString(R.string.ai_chat_regenerate) + "，" + it.statusText);
            }
            if (st != ChatItem.AnswerState.ERROR) {
                b.aiAnswerStatus.setAlpha(1f);
            }
        }

        private void bindFooter(@NonNull ChatItem it, @NonNull ChatItem.AnswerState st, @Nullable AiMessageVO m) {
            boolean hasText = it.partialText != null && !it.partialText.trim().isEmpty();
            boolean done = st == ChatItem.AnswerState.DONE && m != null;
            // 声明：完成、或停止 / 中断 / 超时但已有文字；正在思考、生成中、发送失败不显示
            boolean showDisclaimer = done || ((st == ChatItem.AnswerState.STOPPED || st == ChatItem.AnswerState.ERROR)
                    && hasText);
            b.aiAnswerFooter.setVisibility(showDisclaimer ? View.VISIBLE : View.GONE);
            boolean rate = done && m.isCanRate();
            boolean copy = done && m.isCanCopy();
            b.aiAnswerUp.setVisibility(rate ? View.VISIBLE : View.GONE);
            b.aiAnswerDown.setVisibility(rate ? View.VISIBLE : View.GONE);
            b.aiAnswerCopy.setVisibility(copy ? View.VISIBLE : View.GONE);
            if (rate) {
                boolean up = AiCodes.RATING_USEFUL.equals(m.getRating());
                boolean down = AiCodes.RATING_USELESS.equals(m.getRating());
                b.aiAnswerUp.setSelected(up);
                b.aiAnswerDown.setSelected(down);
                b.aiAnswerUp.setImageResource(up ? com.zhimu.slmp.base.R.drawable.ic_thumb_up_filled
                        : com.zhimu.slmp.base.R.drawable.ic_thumb_up);
                b.aiAnswerDown.setImageResource(down ? com.zhimu.slmp.base.R.drawable.ic_thumb_down_filled
                        : com.zhimu.slmp.base.R.drawable.ic_thumb_down);
                b.aiAnswerUp.setOnClickListener(v -> listener.onRate(it.roundId, AiCodes.RATING_USEFUL));
                b.aiAnswerDown.setOnClickListener(v -> listener.onRate(it.roundId, AiCodes.RATING_USELESS));
            }
            if (copy) {
                b.aiAnswerCopy.setOnClickListener(v -> listener.onCopy(it.roundId));
            }
        }

        private void bindCorrection(@Nullable AiMessageVO m) {
            if (m == null || m.getCorrection() == null || m.getCorrection().getContent() == null) {
                b.aiAnswerCorrection.setVisibility(View.GONE);
                return;
            }
            Context c = b.getRoot().getContext();
            b.aiAnswerCorrectionTitle.setText(c.getString(R.string.ai_chat_correction_title,
                    m.getCorrection().getDisplayTime() == null ? "" : m.getCorrection().getDisplayTime()));
            b.aiAnswerCorrectionText.setText(m.getCorrection().getContent());
            b.aiAnswerCorrection.setVisibility(View.VISIBLE);
        }
    }
}
