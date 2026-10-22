package com.zhimu.slmp.health.ui.common;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.health.R;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * 登记表单页的共用视图逻辑：下拉框、单选 / 多选 Chip、放弃登记对话框、停止输入后再处理（防抖）、滚动到字段。
 */
public final class HealthForms {

    private HealthForms() {
    }

    /** 下拉框：设置适配器与选择回调（不可选的项忽略） */
    @NonNull
    public static OptionAdapter bindDropdown(@NonNull MaterialAutoCompleteTextView view,
                                             @NonNull LongConsumer onSelected) {
        OptionAdapter adapter = new OptionAdapter(view.getContext());
        view.setAdapter(adapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = adapter.getItem(position);
            if (item != null && !item.disabled) {
                onSelected.accept(item.id);
            }
        });
        return adapter;
    }

    /** 按选中 ID 显示下拉框文字 */
    public static void showSelected(@NonNull MaterialAutoCompleteTextView view, @NonNull OptionAdapter adapter,
                                    @Nullable Long id) {
        OptionAdapter.Item item = adapter.findById(id);
        String text = item == null ? "" : item.title;
        if (!Objects.equals(view.getText() == null ? null : view.getText().toString(), text)) {
            view.setText(text, false);
        }
    }

    /** 单选 Chip 行（字典项）；codes 不为空时只列这些编码（消毒剂适用的方式） */
    public static void renderChoiceChips(@NonNull ChipGroup group, @Nullable List<DictItemVO> items,
                                         @Nullable Collection<String> codes, @Nullable String selected,
                                         @NonNull Consumer<String> onSelected) {
        group.removeAllViews();
        if (items == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (DictItemVO item : items) {
            if (codes != null && !codes.isEmpty() && !codes.contains(item.getCode())) {
                continue;
            }
            Chip chip = (Chip) inflater.inflate(R.layout.health_item_choice_chip, group, false);
            chip.setText(item.getName());
            chip.setTag(item.getCode());
            chip.setChecked(Objects.equals(item.getCode(), selected));
            // 可勾选的 Chip 点击时先切换自身状态，不做防连点，选中状态由页面按 ViewModel 回写
            chip.setOnClickListener(v -> onSelected.accept(item.getCode()));
            group.addView(chip);
        }
    }

    /** 同步单选 Chip 的选中状态 */
    public static void checkChip(@NonNull ChipGroup group, @Nullable String code) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(Objects.equals(child.getTag(), code));
            }
        }
    }

    /** “放弃这次登记？”对话框（按钮“继续填写”“放弃”） */
    public static void showDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        ConfirmDialogFragment.builder(requestKey)
                .title(activity.getString(R.string.health_discard_title))
                .message(activity.getString(R.string.health_discard_message))
                .negative(activity.getString(R.string.health_action_keep_editing))
                .positive(activity.getString(R.string.health_action_discard))
                .build()
                .showSafely(activity.getSupportFragmentManager());
    }

    /** 停止输入 delayMs 后执行（剂量、数量、疗程变化后重查批号或预估） */
    public static void afterTextIdle(@NonNull EditText input, long delayMs, @NonNull Runnable action) {
        Handler handler = new Handler(Looper.getMainLooper());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(action, delayMs);
            }
        });
        input.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                handler.removeCallbacksAndMessages(null);
            }
        });
    }

    /** 滚动到字段（校验失败或 40001 时） */
    public static void scrollTo(@NonNull NestedScrollView scroll, @Nullable View field) {
        if (field == null || field.getVisibility() != View.VISIBLE) {
            return;
        }
        scroll.post(() -> {
            int y = 0;
            View v = field;
            while (v != null && v != scroll) {
                y += v.getTop();
                v = v.getParent() instanceof View ? (View) v.getParent() : null;
            }
            scroll.smoothScrollTo(0, Math.max(0, y - field.getResources().getDimensionPixelSize(
                    com.zhimu.slmp.base.R.dimen.base_space_6)));
        });
    }
}
