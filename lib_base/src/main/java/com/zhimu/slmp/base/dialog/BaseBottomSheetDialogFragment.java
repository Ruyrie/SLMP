package com.zhimu.slmp.base.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ButtonLoading;

/**
 * 第 4 级弹窗：底部面板基类（02 号《UI设计规范》7.12、8.1 节）。用于选择，或不超过 3 个字段的快捷操作。
 * <p>
 * 结构：拖动条 → 标题 title-l（左 24 dp）→ 说明（可选）→ 内容区（子类提供，超出时在面板内滚动）→ 底部主按钮（只有快捷操作才有）。
 * 外观来自主题 {@code ThemeOverlay.Zmt.BottomSheetDialog}：surface-container-low 底，顶部两角 28 dp，遮罩 32%。
 * 下拉、点遮罩、按返回键关闭；打开期间背后页面（含 FAB、底部导航）不响应。
 * <p>
 * 子类：
 * <ul>
 *     <li>{@link #onCreateContentView} 提供内容区；内容左右边距自行设置（规范为 24 dp，{@code @dimen/base_sheet_padding_horizontal}）</li>
 *     <li>标题与说明默认取 arguments 中的 {@link #ARG_TITLE}、{@link #ARG_DESCRIPTION}，也可重写 {@link #getSheetTitle()}</li>
 *     <li>快捷操作重写 {@link #getPrimaryButtonText()} 与 {@link #onPrimaryButtonClick()}，提交中 {@link #setPrimaryLoading(boolean)}</li>
 *     <li>结果用 {@link #setResultAndDismiss(Bundle)} 经 FragmentResult 回传（requestKey 为 {@link #ARG_REQUEST_KEY}），
 *     不在面板里保存回调对象；可通过父 Fragment / Activity 的 ViewModel 读写数据</li>
 * </ul>
 * 显示：{@link #showSafely(FragmentManager)}（经 {@link DialogGuard}，同一时刻最多一个弹窗）。
 * 面板中的操作需要确认时，先 {@code dismiss()} 面板再弹对话框；面板中的日期、时间字段可直接用 {@link DateTimePicker}。
 */
public abstract class BaseBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public static final String ARG_REQUEST_KEY = "base_sheet_request_key";
    public static final String ARG_TITLE = "base_sheet_title";
    public static final String ARG_DESCRIPTION = "base_sheet_description";
    public static final String ARG_EXTRAS = "base_sheet_extras";

    /** 结果中透传的 Bundle（来自 {@link #ARG_EXTRAS}） */
    public static final String RESULT_EXTRAS = "extras";

    @Nullable
    private TextView titleView;
    @Nullable
    private TextView descriptionView;
    @Nullable
    private MaterialButton primaryButton;

    /**
     * 提供内容区视图（可返回 null）。
     *
     * @param container 内容区容器，inflate 时作为 parent 传入但不要 attach
     */
    @Nullable
    protected abstract View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                                @Nullable Bundle savedInstanceState);

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                   @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.base_dialog_bottom_sheet, container, false);
        titleView = root.findViewById(R.id.base_sheet_title);
        descriptionView = root.findViewById(R.id.base_sheet_description);
        FrameLayout content = root.findViewById(R.id.base_sheet_content);
        View footer = root.findViewById(R.id.base_sheet_footer);
        primaryButton = root.findViewById(R.id.base_sheet_primary);

        setTitle(getSheetTitle());
        setDescription(getSheetDescription());

        View contentView = onCreateContentView(inflater, content, savedInstanceState);
        if (contentView != null) {
            content.addView(contentView);
        } else {
            content.setVisibility(View.GONE);
        }

        CharSequence primaryText = getPrimaryButtonText();
        if (!TextUtils.isEmpty(primaryText)) {
            footer.setVisibility(View.VISIBLE);
            primaryButton.setText(primaryText);
            int icon = getPrimaryButtonIcon();
            if (icon != 0) {
                primaryButton.setIconResource(icon);
            }
            ClickThrottle.setOnClickListener(primaryButton, v -> onPrimaryButtonClick());
        }
        return root;
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) dialog).getBehavior();
            if (expandOnShow()) {
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    /**
     * 打开时是否直接完全展开（默认有底部按钮的快捷操作面板展开，保证按钮可见）；
     * 选择面板内容超过半屏时先显示一部分，可上拉到状态栏下方。
     */
    protected boolean expandOnShow() {
        return !TextUtils.isEmpty(getPrimaryButtonText());
    }

    // ---------------- 标题与说明 ----------------

    @Nullable
    protected CharSequence getSheetTitle() {
        Bundle args = getArguments();
        return args == null ? null : args.getCharSequence(ARG_TITLE);
    }

    @Nullable
    protected CharSequence getSheetDescription() {
        Bundle args = getArguments();
        return args == null ? null : args.getCharSequence(ARG_DESCRIPTION);
    }

    protected void setTitle(@Nullable CharSequence title) {
        if (titleView != null) {
            titleView.setText(title);
            titleView.setVisibility(TextUtils.isEmpty(title) ? View.GONE : View.VISIBLE);
        }
    }

    protected void setDescription(@Nullable CharSequence description) {
        if (descriptionView != null) {
            descriptionView.setText(description);
            descriptionView.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
        }
    }

    // ---------------- 底部主按钮 ----------------

    /** 底部主按钮文字；返回 null 表示没有底部按钮（选择面板） */
    @Nullable
    protected CharSequence getPrimaryButtonText() {
        return null;
    }

    @DrawableRes
    protected int getPrimaryButtonIcon() {
        return 0;
    }

    protected void onPrimaryButtonClick() {
    }

    @Nullable
    protected MaterialButton getPrimaryButton() {
        return primaryButton;
    }

    /** 提交中：按钮内显示环形进度并不再响应点击，提交成功后关闭面板并由页面用 Snackbar 告知结果 */
    protected void setPrimaryLoading(boolean loading) {
        if (primaryButton != null) {
            ButtonLoading.setLoading(primaryButton, loading);
        }
        setCancelable(!loading);
    }

    // ---------------- 结果 ----------------

    @Nullable
    protected String getRequestKey() {
        Bundle args = getArguments();
        return args == null ? null : args.getString(ARG_REQUEST_KEY);
    }

    /** 经 FragmentResult 回传结果（自动带上 {@link #RESULT_EXTRAS}）；没有 requestKey 时忽略 */
    protected void setResult(@NonNull Bundle result) {
        String key = getRequestKey();
        if (key == null || !isAdded()) {
            return;
        }
        Bundle args = getArguments();
        Bundle extras = args == null ? null : args.getBundle(ARG_EXTRAS);
        if (extras != null && !result.containsKey(RESULT_EXTRAS)) {
            result.putBundle(RESULT_EXTRAS, extras);
        }
        getParentFragmentManager().setFragmentResult(key, result);
    }

    protected void setResultAndDismiss(@NonNull Bundle result) {
        setResult(result);
        dismiss();
    }

    // ---------------- 显示 ----------------

    /** 按 {@link DialogGuard} 规则显示，标签为类名 */
    public boolean showSafely(@NonNull FragmentManager fm) {
        return showSafely(fm, getClass().getName());
    }

    public boolean showSafely(@NonNull FragmentManager fm, @NonNull String tag) {
        return DialogGuard.show(this, fm, tag);
    }

    /**
     * 组装基础参数，子类的 newInstance 中使用。
     *
     * @param requestKey 结果键，可为空（只展示、不回传结果）
     */
    @NonNull
    public static Bundle baseArgs(@Nullable String requestKey, @Nullable CharSequence title,
                                  @Nullable CharSequence description, @Nullable Bundle extras) {
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_KEY, requestKey);
        args.putCharSequence(ARG_TITLE, title);
        args.putCharSequence(ARG_DESCRIPTION, description);
        if (extras != null) {
            args.putBundle(ARG_EXTRAS, extras);
        }
        return args;
    }
}
