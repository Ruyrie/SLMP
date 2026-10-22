package com.zhimu.slmp.account.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.zhimu.slmp.account.util.RecoveryCodeWatcher;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;

/**
 * 恢复码显示（A04、A06 第 2 步）：16 位分两行、每行两组、每组 4 位，32 sp 等宽字形，字距约 2 dp，组间约 40 dp。
 * {@link #createBitmap} 按 UI 设计文档“保存为图片”的版式绘制白底竖版位图（在 io 线程调用）。
 */
public class RecoveryCodeView extends AppCompatTextView {

    /** 组间分隔（全角空格两个，约 40 dp） */
    private static final String GROUP_GAP = "　　";

    public RecoveryCodeView(@NonNull Context context) {
        this(context, null);
    }

    public RecoveryCodeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        setLetterSpacing(0.06f);
        setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics()), 1f);
        setGravity(Gravity.CENTER);
        setTextColor(ContextCompat.getColor(context, com.zhimu.slmp.base.R.color.zm_on_surface));
        setTextIsSelectable(false);
    }

    /** 设置恢复码（任意格式，自动规范化并分组） */
    public void setCode(@Nullable String code) {
        setText(twoLines(code));
    }

    /** “8KQ4　T2MW\n7HXN　3PDR” */
    @NonNull
    public static String twoLines(@Nullable String code) {
        String c = RecoveryCodeWatcher.normalize(code);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length(); i += 4) {
            if (i == 8) {
                sb.append('\n');
            } else if (i > 0) {
                sb.append(GROUP_GAP);
            }
            sb.append(c, i, Math.min(i + 4, c.length()));
        }
        return sb.toString();
    }

    /** “8KQ4 T2MW 7HXN 3PDR”（复制到剪贴板的格式） */
    @NonNull
    public static String groups(@Nullable String code) {
        return RecoveryCodeWatcher.format(code);
    }

    /**
     * 绘制保存到相册的竖版 PNG：顶部“智牧通 · 账号恢复码”，“账号 138****6620”“生成于 10月19日 周一”，
     * 中部恢复码分两行大字，底部“手机号不能使用时，用这个恢复码找回账号”。
     */
    @WorkerThread
    @NonNull
    public static Bitmap createBitmap(@NonNull Context context, @NonNull RecoveryCodeVO vo) {
        final int width = 1080;
        final int height = 1440;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(ContextCompat.getColor(context, com.zhimu.slmp.base.R.color.zm_surface_container_lowest));

        int onSurface = ContextCompat.getColor(context, com.zhimu.slmp.base.R.color.zm_on_surface);
        int onSurfaceVariant = ContextCompat.getColor(context, com.zhimu.slmp.base.R.color.zm_on_surface_variant);
        int primary = ContextCompat.getColor(context, com.zhimu.slmp.base.R.color.zm_primary);

        TextPaint title = paint(64, primary, Typeface.DEFAULT_BOLD);
        TextPaint body = paint(40, onSurfaceVariant, Typeface.DEFAULT);
        TextPaint code = paint(112, onSurface, Typeface.MONOSPACE);
        code.setLetterSpacing(0.08f);
        TextPaint foot = paint(36, onSurfaceVariant, Typeface.DEFAULT);

        float cx = width / 2f;
        canvas.drawText("智牧通 · 账号恢复码", cx, 200, title);
        String phone = vo.getPhoneMasked() == null ? "" : vo.getPhoneMasked();
        canvas.drawText("账号 " + phone, cx, 320, body);
        String generated = DateFormatter.formatDate(vo.getGeneratedAt());
        canvas.drawText("生成于 " + generated, cx, 390, body);

        String[] lines = twoLines(vo.getCode()).split("\n");
        float y = 640;
        for (String line : lines) {
            canvas.drawText(line.replace(GROUP_GAP, "  "), cx, y, code);
            y += 170;
        }
        canvas.drawText("手机号不能使用时，用这个恢复码找回账号", cx, 1240, foot);
        return bmp;
    }

    @NonNull
    private static TextPaint paint(float sizePx, int color, @NonNull Typeface typeface) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(sizePx);
        p.setColor(color);
        p.setTypeface(typeface);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }
}
