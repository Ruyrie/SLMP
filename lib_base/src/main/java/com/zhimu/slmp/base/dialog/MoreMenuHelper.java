package com.zhimu.slmp.base.dialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 第 3 级弹窗：“更多”菜单（02 号《UI设计规范》7.11、8.1 节），收纳某条记录或某个页面的更多操作。
 * <p>
 * 从触发它的 {@code more_vert} 下方弹出、右缘对齐；宽 112–280 dp，圆角 12 dp，surface-container 底，elevation-2；
 * 每项高 48 dp，前置图标 24 dp，文字 body-m，右侧附注 body-s。展开期间 {@code more_vert} 显示 40 dp
 * secondary-container 圆底。点外部或按返回键关闭。
 * <ul>
 *     <li>需要审核的操作写“提交修改申请”并在右侧注明“需审核”：{@link Builder#addReview}；菜单中不出现“编辑”</li>
 *     <li>危险项（作废、停用、删除）放在最后，用 1 dp outline-variant 分隔线隔开，文字和图标 error 色：{@link Builder#addDanger}</li>
 *     <li>员工无权的项不加入</li>
 * </ul>
 * <pre>
 * MoreMenuHelper.with(anchor)
 *         .add(R.id.herd_menu_transfer, "转栏", R.drawable.ic_swap_horiz)
 *         .add(R.id.herd_menu_death, "登记死淘", R.drawable.ic_heart_minus)
 *         .addReview(R.id.herd_menu_change, "提交修改申请", R.drawable.ic_edit_note)
 *         .setOnItemClickListener(itemId -&gt; viewModel.onMenu(itemId))
 *         .show();
 * </pre>
 * 顶部栏菜单项作为锚点时，用 {@code toolbar.findViewById(R.id.menu_more)} 取得视图。菜单不跨屏幕旋转保留。
 */
public final class MoreMenuHelper {

    /** 菜单项点击 */
    public interface OnItemClickListener {
        void onMenuItemClick(int itemId);
    }

    /** 菜单项 */
    public static final class Item {
        final int id;
        @NonNull
        final CharSequence title;
        @DrawableRes
        final int icon;
        @Nullable
        final CharSequence description;
        @Nullable
        final CharSequence note;
        final boolean danger;
        final boolean enabled;
        /** primary 色、排在分隔线后的项（例如已停用栏舍的“启用栏舍”） */
        final boolean primary;

        public Item(int id, @NonNull CharSequence title, @DrawableRes int icon, @Nullable CharSequence description,
                    @Nullable CharSequence note, boolean danger, boolean enabled) {
            this(id, title, icon, description, note, danger, enabled, false);
        }

        public Item(int id, @NonNull CharSequence title, @DrawableRes int icon, @Nullable CharSequence description,
                    @Nullable CharSequence note, boolean danger, boolean enabled, boolean primary) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.description = description;
            this.note = note;
            this.danger = danger;
            this.enabled = enabled;
            this.primary = primary;
        }

        /** 排在分隔线后：危险项与 primary 项 */
        boolean isTrailing() {
            return danger || primary;
        }
    }

    private MoreMenuHelper() {
    }

    @NonNull
    public static Builder with(@NonNull View anchor) {
        return new Builder(anchor);
    }

    /** 菜单构建器 */
    public static final class Builder {
        private final View anchor;
        private final List<Item> items = new ArrayList<>();
        @Nullable
        private OnItemClickListener listener;

        Builder(@NonNull View anchor) {
            this.anchor = anchor;
        }

        /** 普通项 */
        @NonNull
        public Builder add(int id, @NonNull CharSequence title, @DrawableRes int icon) {
            items.add(new Item(id, title, icon, null, null, false, true));
            return this;
        }

        /** 需审核的项：右侧附注“需审核” */
        @NonNull
        public Builder addReview(int id, @NonNull CharSequence title, @DrawableRes int icon) {
            items.add(new Item(id, title, icon, null, anchor.getContext().getString(R.string.base_menu_needs_review),
                    false, true));
            return this;
        }

        /** 危险项：error 色，排在最后并在前面加分隔线 */
        @NonNull
        public Builder addDanger(int id, @NonNull CharSequence title, @DrawableRes int icon) {
            items.add(new Item(id, title, icon, null, null, true, true));
            return this;
        }

        /** primary 色项：图标和文字 primary 色，与危险项一样排在分隔线后，例如已停用栏舍的“启用栏舍” */
        @NonNull
        public Builder addPrimary(int id, @NonNull CharSequence title, @DrawableRes int icon) {
            items.add(new Item(id, title, icon, null, null, false, true, true));
            return this;
        }

        /** 完整参数的项（带一行说明、附注、不可用状态，如“停用栏舍 / 栏舍里还有 1 个在栏批次……”） */
        @NonNull
        public Builder add(@NonNull Item item) {
            items.add(item);
            return this;
        }

        @NonNull
        public Builder setOnItemClickListener(@Nullable OnItemClickListener l) {
            this.listener = l;
            return this;
        }

        /** 显示菜单；没有菜单项时不显示，返回 null */
        @Nullable
        public ListPopupWindow show() {
            if (items.isEmpty()) {
                return null;
            }
            TipPopup.dismiss();
            List<Item> ordered = new ArrayList<>();
            for (Item i : items) {
                if (!i.isTrailing()) {
                    ordered.add(i);
                }
            }
            int firstDanger = ordered.size();
            for (Item i : items) {
                if (i.isTrailing()) {
                    ordered.add(i);
                }
            }
            Context context = anchor.getContext();
            ListPopupWindow popup = new ListPopupWindow(context);
            MenuAdapter adapter = new MenuAdapter(context, ordered, firstDanger > 0 ? firstDanger : -1);
            popup.setAdapter(adapter);
            popup.setAnchorView(anchor);
            popup.setModal(true);
            popup.setDropDownGravity(Gravity.END);
            popup.setBackgroundDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context,
                    R.drawable.base_bg_menu));
            popup.setContentWidth(measureWidth(context, adapter));
            popup.setOnItemClickListener((parent, view, position, id) -> {
                Item item = ordered.get(position);
                if (!item.enabled) {
                    return;
                }
                popup.dismiss();
                if (listener != null) {
                    listener.onMenuItemClick(item.id);
                }
            });
            Runnable restore = highlightAnchor(anchor);
            popup.setOnDismissListener(restore::run);
            popup.show();
            return popup;
        }
    }

    private static int measureWidth(@NonNull Context context, @NonNull MenuAdapter adapter) {
        int min = context.getResources().getDimensionPixelSize(R.dimen.base_menu_min_width);
        int max = context.getResources().getDimensionPixelSize(R.dimen.base_menu_max_width);
        int width = 0;
        ViewGroup parent = new android.widget.FrameLayout(context);
        int spec = View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST);
        for (int i = 0; i < adapter.getCount(); i++) {
            View v = adapter.getView(i, null, parent);
            v.measure(spec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            width = Math.max(width, v.getMeasuredWidth());
        }
        return Math.max(min, Math.min(max, width));
    }

    /** 展开期间给锚点加 40 dp secondary-container 圆底，图标改为 on-secondary-container；返回恢复操作 */
    @NonNull
    private static Runnable highlightAnchor(@NonNull View anchor) {
        Drawable oldBackground = anchor.getBackground();
        int size = anchor.getResources().getDimensionPixelSize(R.dimen.base_list_leading_size);
        int insetX = Math.max(0, (anchor.getWidth() - size) / 2);
        int insetY = Math.max(0, (anchor.getHeight() - size) / 2);
        MaterialShapeDrawable circle = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(size / 2f)
                .build());
        circle.setFillColor(ColorStateList.valueOf(MaterialColors.getColor(anchor,
                com.google.android.material.R.attr.colorSecondaryContainer)));
        ColorStateList onContainer = ColorStateList.valueOf(MaterialColors.getColor(anchor,
                com.google.android.material.R.attr.colorOnSecondaryContainer));

        Runnable restoreTint = () -> {
        };
        if (anchor instanceof MaterialButton) {
            MaterialButton b = (MaterialButton) anchor;
            ColorStateList old = b.getIconTint();
            ColorStateList oldBg = b.getBackgroundTintList();
            b.setIconTint(onContainer);
            b.setBackgroundTintList(circle.getFillColor());
            restoreTint = () -> {
                b.setIconTint(old);
                b.setBackgroundTintList(oldBg);
            };
            return restoreTint;
        } else if (anchor instanceof ImageView) {
            ImageView iv = (ImageView) anchor;
            ColorStateList old = ImageViewCompat.getImageTintList(iv);
            ImageViewCompat.setImageTintList(iv, onContainer);
            restoreTint = () -> ImageViewCompat.setImageTintList(iv, old);
        } else if (anchor instanceof TextView) {
            // 顶部栏菜单项（ActionMenuItemView）
            TextView tv = (TextView) anchor;
            ColorStateList old = TextViewCompat.getCompoundDrawableTintList(tv);
            TextViewCompat.setCompoundDrawableTintList(tv, onContainer);
            restoreTint = () -> TextViewCompat.setCompoundDrawableTintList(tv, old);
        }
        anchor.setBackground(new InsetDrawable(circle, insetX, insetY, insetX, insetY));
        final Runnable tint = restoreTint;
        return () -> {
            anchor.setBackground(oldBackground);
            tint.run();
        };
    }

    /** 菜单适配器 */
    private static final class MenuAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final List<Item> items;
        private final int firstDanger;
        private final int errorColor;
        private final int primaryColor;
        private final int onSurface;
        private final int onSurfaceVariant;

        MenuAdapter(@NonNull Context context, @NonNull List<Item> items, int firstDanger) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.firstDanger = firstDanger;
            this.errorColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, 0);
            this.primaryColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0);
            this.onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0);
            this.onSurfaceVariant = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Item getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public boolean isEnabled(int position) {
            return items.get(position).enabled;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView != null ? convertView
                    : inflater.inflate(R.layout.base_item_more_menu, parent, false);
            Item item = items.get(position);
            View divider = v.findViewById(R.id.base_menu_divider);
            ImageView icon = v.findViewById(R.id.base_menu_icon);
            TextView title = v.findViewById(R.id.base_menu_title);
            TextView description = v.findViewById(R.id.base_menu_description);
            TextView note = v.findViewById(R.id.base_menu_note);

            divider.setVisibility(position == firstDanger ? View.VISIBLE : View.GONE);
            if (item.icon != 0) {
                icon.setImageResource(item.icon);
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.GONE);
            }
            int textColor = item.danger ? errorColor : item.primary ? primaryColor : onSurface;
            int iconColor = item.danger ? errorColor : item.primary ? primaryColor : onSurfaceVariant;
            title.setText(item.title);
            title.setTextColor(item.enabled ? textColor : onSurfaceVariant);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(iconColor));
            description.setText(item.description);
            description.setVisibility(TextUtils.isEmpty(item.description) ? View.GONE : View.VISIBLE);
            note.setText(item.note);
            note.setVisibility(TextUtils.isEmpty(item.note) ? View.GONE : View.VISIBLE);
            v.setAlpha(item.enabled ? 1f : 0.6f);
            return v;
        }
    }
}
