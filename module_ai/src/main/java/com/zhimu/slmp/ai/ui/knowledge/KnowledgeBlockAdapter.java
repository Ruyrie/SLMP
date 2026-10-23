package com.zhimu.slmp.ai.ui.knowledge;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiItemKnowledgeBlockBinding;
import com.zhimu.slmp.ai.model.vo.KnowledgeBlockVO;
import com.zhimu.slmp.ai.util.KnowledgeHtmlSanitizer;

import java.util.ArrayList;
import java.util.List;

/**
 * K03 正文段落（按 para_type 五种样式）；highlight 位置的段落为“AI 回答引用了这一段”引用块。
 */
public class KnowledgeBlockAdapter extends RecyclerView.Adapter<KnowledgeBlockAdapter.Holder> {

    private final List<KnowledgeBlockVO> items = new ArrayList<>();
    private int highlight = -1;

    public void submit(@NonNull List<KnowledgeBlockVO> list, int highlightIndex) {
        items.clear();
        items.addAll(list);
        highlight = highlightIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AiItemKnowledgeBlockBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        KnowledgeBlockVO b = items.get(position);
        boolean cited = position == highlight;
        String type = b.getParaType() == null ? "TEXT" : b.getParaType();
        float d = h.itemView.getResources().getDisplayMetrics().density;
        h.b.aiBlockCited.setVisibility(cited ? View.VISIBLE : View.GONE);
        if (cited) {
            h.b.aiBlockRoot.setBackgroundResource(R.drawable.ai_bg_quote);
            h.b.aiBlockRoot.setPadding(Math.round(13 * d), Math.round(8 * d), Math.round(12 * d), Math.round(12 * d));
        } else {
            h.b.aiBlockRoot.setBackground(null);
            h.b.aiBlockRoot.setPadding(0, 0, 0, 0);
        }
        if ("IMAGE".equals(type)) {
            h.b.aiBlockText.setVisibility(b.getText() == null || b.getText().isEmpty() ? View.GONE : View.VISIBLE);
            h.b.aiBlockText.setText(b.getText());
            h.b.aiBlockImage.setVisibility(View.VISIBLE);
            Glide.with(h.b.aiBlockImage).load(b.getImageUrl()).into(h.b.aiBlockImage);
            return;
        }
        h.b.aiBlockImage.setVisibility(View.GONE);
        h.b.aiBlockText.setVisibility(View.VISIBLE);
        if (b.getContentHtml() != null && !b.getContentHtml().isEmpty()) {
            h.b.aiBlockText.setText(HtmlCompat.fromHtml(KnowledgeHtmlSanitizer.sanitize(b.getContentHtml()),
                    HtmlCompat.FROM_HTML_MODE_COMPACT));
        } else {
            h.b.aiBlockText.setText(b.getText());
        }
        if ("HEADING".equals(type)) {
            h.b.aiBlockText.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_TitleMedium);
            h.b.aiBlockText.setTypeface(null, Typeface.BOLD);
        } else {
            h.b.aiBlockText.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyLarge);
            h.b.aiBlockText.setTypeface(null, "QUOTE".equals(type) ? Typeface.ITALIC : Typeface.NORMAL);
        }
        h.b.aiBlockText.setTextColor(com.google.android.material.color.MaterialColors.getColor(h.b.aiBlockText,
                "QUOTE".equals(type) ? com.google.android.material.R.attr.colorOnSurfaceVariant
                        : com.google.android.material.R.attr.colorOnSurface));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final AiItemKnowledgeBlockBinding b;

        Holder(@NonNull AiItemKnowledgeBlockBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
