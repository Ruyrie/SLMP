package com.zhimu.slmp.herd.ui.common;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.widget.NoticeView;
import com.zhimu.slmp.herd.R;

import java.util.function.LongConsumer;

/**
 * 登记表单页的共用视图逻辑：超容量横幅与确认复选框、放弃登记对话框、下拉框选择。
 */
public final class FormViews {

    private FormViews() {
    }

    /** 超容量横幅：出现时展开（250 毫秒），复选框与 ViewModel 的 capacityChecked 双向同步 */
    public static void bindCapacityBanner(@NonNull LifecycleOwner owner, @NonNull NoticeView banner,
                                          @NonNull LiveData<String> text, @NonNull MutableLiveData<Boolean> checked,
                                          @NonNull String title) {
        text.observe(owner, t -> {
            boolean show = t != null;
            if (show) {
                banner.setNotice(title, t);
            }
            if (show != (banner.getVisibility() == View.VISIBLE)) {
                banner.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    banner.setAlpha(0f);
                    banner.animate().alpha(1f).setDuration(250).start();
                }
            }
        });
        banner.setOnCheckedChangeListener((b, isChecked) -> {
            if (!Boolean.valueOf(isChecked).equals(checked.getValue())) {
                checked.setValue(isChecked);
            }
        });
        checked.observe(owner, c -> {
            boolean v = Boolean.TRUE.equals(c);
            if (banner.isChecked() != v) {
                banner.setChecked(v);
            }
        });
    }

    /** “放弃这次登记？”对话框：确认后关闭页面 */
    public static void showDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        ConfirmDialogFragment.builder(requestKey)
                .title(activity.getString(R.string.herd_discard_title))
                .message(activity.getString(R.string.herd_discard_message))
                .negative(activity.getString(R.string.herd_action_keep_editing))
                .positive(activity.getString(R.string.herd_action_discard))
                .build()
                .showSafely(activity.getSupportFragmentManager());
    }

    public static void listenDiscard(@NonNull AppCompatActivity activity, @NonNull String requestKey) {
        activity.getSupportFragmentManager().setFragmentResultListener(requestKey, activity, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                activity.finish();
            }
        });
    }

    /** 下拉框：设置适配器与选择回调（不可选的项忽略） */
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
        view.setText(item == null ? "" : item.title, false);
    }
}
