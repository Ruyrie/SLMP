package com.zhimu.slmp.base.binding;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.zhimu.slmp.base.glide.FileGlideUrl;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;

import java.util.Collection;

/**
 * 通用 BindingAdapter：图片（按 file_id 缓存）、字段为 null 时隐藏整行、数量 / 日期 / 金额格式化、防连点点击。
 * <pre>
 * app:imageUrl="@{vo.thumbUrl}" app:imageCacheKey="@{vo.fileId}"
 * app:goneIfNull="@{vo.unitPrice}"
 * app:quantityText="@{vo.headCount}" app:quantityUnit="@{vo.countUnitText}"
 * app:dateText="@{vo.planDate}"  app:dateTimeText="@{vo.createdAt}"  app:amountText="@{vo.amount}"
 * app:onSafeClick="@{() -> vm.submit()}"
 * </pre>
 */
public final class CommonBindingAdapters {

    private CommonBindingAdapters() {
    }

    // ===== 图片 =====

    /**
     * Glide 加载网络图片；cacheKey 为 file_id 时按 file_id 缓存（临时地址变化不重复下载）。
     */
    @BindingAdapter(value = {"imageUrl", "imageCacheKey", "imagePlaceholder", "imageCircle"}, requireAll = false)
    public static void loadImage(ImageView view, @Nullable String url, @Nullable Long cacheKey,
                                 @Nullable Drawable placeholder, @Nullable Boolean circle) {
        if (url == null || url.isEmpty()) {
            Glide.with(view).clear(view);
            view.setImageDrawable(placeholder);
            return;
        }
        Object model = cacheKey != null ? new FileGlideUrl(url, "file_" + cacheKey) : url;
        RequestBuilder<Drawable> req = Glide.with(view).load(model);
        if (placeholder != null) {
            req = req.placeholder(placeholder).error(placeholder);
        }
        if (Boolean.TRUE.equals(circle)) {
            req = req.circleCrop();
        }
        req.into(view);
    }

    // ===== 显隐 =====

    /** 值为 null（员工视图缺失的字段）或空字符串、空集合时隐藏整行 */
    @BindingAdapter("goneIfNull")
    public static void goneIfNull(View view, @Nullable Object value) {
        boolean empty = value == null
                || (value instanceof CharSequence && ((CharSequence) value).length() == 0)
                || (value instanceof Collection && ((Collection<?>) value).isEmpty());
        view.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @BindingAdapter("visibleIf")
    public static void visibleIf(View view, @Nullable Boolean visible) {
        view.setVisibility(Boolean.TRUE.equals(visible) ? View.VISIBLE : View.GONE);
    }

    @BindingAdapter("invisibleIf")
    public static void invisibleIf(View view, @Nullable Boolean invisible) {
        view.setVisibility(Boolean.TRUE.equals(invisible) ? View.INVISIBLE : View.VISIBLE);
    }

    // ===== 文本格式化 =====

    /** 数量加单位：“1,200 千克”；quantityScale 固定小数位（平均体重 1 位） */
    @BindingAdapter(value = {"quantityText", "quantityUnit", "quantityScale"}, requireAll = false)
    public static void setQuantity(TextView view, @Nullable Number value, @Nullable String unit,
                                   @Nullable Integer scale) {
        view.setText(scale == null
                ? NumberFormatter.formatQuantity(value, unit)
                : NumberFormatter.formatQuantity(value, scale, unit));
    }

    /** 金额两位小数：“3,200.00 元” */
    @BindingAdapter(value = {"amountText", "amountUnit"}, requireAll = false)
    public static void setAmount(TextView view, @Nullable Number value, @Nullable String unit) {
        view.setText(NumberFormatter.formatAmount(value, unit == null ? "元" : unit));
    }

    /** 汇总金额：整数不显示小数“188,160 元” */
    @BindingAdapter("amountSummaryText")
    public static void setAmountSummary(TextView view, @Nullable Number value) {
        view.setText(NumberFormatter.formatAmountSummary(value));
    }

    /** 比例显示为百分比：“92.0%” */
    @BindingAdapter("percentText")
    public static void setPercent(TextView view, @Nullable Number ratio) {
        view.setText(NumberFormatter.formatPercent(ratio));
    }

    /** 日期：“10月19日 周一”；dateWithWeek=false 省略星期 */
    @BindingAdapter(value = {"dateText", "dateWithWeek"}, requireAll = false)
    public static void setDate(TextView view, @Nullable String date, @Nullable Boolean withWeek) {
        view.setText(DateFormatter.formatDate(date, withWeek == null || withWeek));
    }

    /** 日期时间：“今天 14:20”“10月19日 09:28” */
    @BindingAdapter("dateTimeText")
    public static void setDateTime(TextView view, @Nullable String datetime) {
        view.setText(DateFormatter.formatDateTime(datetime));
    }

    // ===== 点击 =====

    /** 防连点点击（500 毫秒） */
    @BindingAdapter("onSafeClick")
    public static void setOnSafeClick(View view, @Nullable View.OnClickListener listener) {
        ClickThrottle.setOnClickListener(view, listener);
    }
}
