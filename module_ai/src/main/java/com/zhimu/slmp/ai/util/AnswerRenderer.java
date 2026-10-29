package com.zhimu.slmp.ai.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.ai.model.vo.AiMessageVO;
import com.zhimu.slmp.ai.model.vo.CitationBriefVO;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * K01 回答正文的 Span（技术文档 2.6.1 第 12 步）：完成后 [^n] 转为 18 dp 圆形可点角标（按 seq_no 对应引用，点击区 32 dp），
 * 来源不足说明独立展示；生成中末尾挂 2 × 20 dp 闪烁光标（{@link CursorSpan}）。
 */
public final class AnswerRenderer {

    public interface OnCitationClick {
        void onCitation(@NonNull CitationBriefVO citation);
    }

    private static final Pattern CITATION = Pattern.compile("\\[\\^(\\d+)]");

    private AnswerRenderer() {
    }

    /** 完成的回答 */
    @NonNull
    public static CharSequence render(@NonNull Context context, @NonNull AiMessageVO m,
                                      @Nullable OnCitationClick listener) {
        String text = AiText.answerBody(m);
        Map<Integer, CitationBriefVO> bySeq = new HashMap<>();
        for (CitationBriefVO c : m.getCitations()) {
            bySeq.put(c.getSeqNo(), c);
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher matcher = CITATION.matcher(text);
        int last = 0;
        while (matcher.find()) {
            sb.append(text, last, matcher.start());
            int seq = Integer.parseInt(matcher.group(1));
            CitationBriefVO c = bySeq.get(seq);
            if (c != null) {
                int start = sb.length();
                // 被角标覆盖的文字用“[n]”而不是对象占位符 U+FFFC：读屏与界面文字导出时读到的是引用序号
                sb.append("[").append(String.valueOf(seq)).append("]");
                sb.setSpan(new CitationBadgeSpan(context, String.valueOf(seq)), start, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (listener != null) {
                    sb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            listener.onCitation(c);
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            // 角标自绘，不加下划线与链接色
                        }
                    }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            last = matcher.end();
        }
        sb.append(text.substring(last));
        return sb;
    }

    /** 生成中的文字 + 光标 */
    @NonNull
    public static CharSequence streaming(@NonNull CharSequence visible, @NonNull CursorSpan cursor) {
        SpannableStringBuilder sb = new SpannableStringBuilder(visible);
        int start = sb.length();
        sb.append('​');
        sb.setSpan(cursor, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 首句结束位置（含句末标点）；找不到返回 -1 */
    static int firstSentenceEnd(@NonNull CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?') {
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static String trimLeading(@NonNull String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    private static float dp(@NonNull Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** 18 dp 圆形角标：surface-container-highest 底、11sp on-surface-variant 序号 */
    public static final class CitationBadgeSpan extends ReplacementSpan {
        private final String label;
        private final float size;
        private final float margin;
        private final float textSize;
        private final int bg;
        private final int fg;
        private final RectF rect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CitationBadgeSpan(@NonNull Context context, @NonNull String label) {
            this.label = label;
            this.size = dp(context, 18);
            this.margin = dp(context, 3);
            this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                    context.getResources().getDisplayMetrics());
            this.bg = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFFDDE3EA);
            this.fg = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF444746);
        }

        @Override
        public int getSize(@NonNull Paint p, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return (int) Math.ceil(size + margin * 2);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, @NonNull Paint p) {
            Paint.FontMetrics m = p.getFontMetrics();
            float centerY = y + (m.ascent + m.descent) / 2f;
            float cx = x + margin + size / 2f;
            rect.set(cx - size / 2f, centerY - size / 2f, cx + size / 2f, centerY + size / 2f);
            paint.setColor(bg);
            canvas.drawOval(rect, paint);
            paint.setColor(fg);
            paint.setTextSize(textSize);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics tm = paint.getFontMetrics();
            canvas.drawText(label, cx, centerY - (tm.ascent + tm.descent) / 2f, paint);
        }
    }

    /** 2 × 20 dp 光标（on-surface、圆角 1 dp），visible 由宿主每 500 ms 切换后 invalidate */
    public static final class CursorSpan extends ReplacementSpan {
        private final float width;
        private final float height;
        private final float radius;
        private final float margin;
        private final int color;
        private final RectF rect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean visible = true;

        public CursorSpan(@NonNull Context context) {
            width = dp(context, 2);
            height = dp(context, 20);
            radius = dp(context, 1);
            margin = dp(context, 2);
            color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF1F1F1F);
        }

        public void setVisible(boolean v) {
            visible = v;
        }

        public boolean isVisible() {
            return visible;
        }

        @Override
        public int getSize(@NonNull Paint p, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return (int) Math.ceil(width + margin);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
                         int bottom, @NonNull Paint p) {
            if (!visible) {
                return;
            }
            Paint.FontMetrics m = p.getFontMetrics();
            float centerY = y + (m.ascent + m.descent) / 2f;
            rect.set(x + margin, centerY - height / 2f, x + margin + width, centerY + height / 2f);
            paint.setColor(color);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }
}
