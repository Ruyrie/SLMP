package com.zhimu.slmp.base.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zhimu.slmp.base.R;

/**
 * 第 5 级弹窗：确认对话框（02 号《UI设计规范》7.11、8.1 节），只用于确认不可撤销的操作，或不能执行时的告知。
 * <p>
 * 宽 312 dp，圆角 28 dp，surface-container-high 底，内边距 24 dp；标题 headline-s 24 sp（用问句，如“忽略这条待办？”；
 * 不能执行时用陈述句），正文 body-m 写后果（最多两句）；最多两个文字按钮，确认在右，写具体动作（“忽略”“作废”），不写“确定”；
 * 只需告知时只放一个“知道了”。危险操作的确认按钮为 error 色；可带顶部 24 dp 图标（secondary 色），此时图标和标题居中。
 * 没有输入框的确认对话框，点遮罩等同于“取消”。
 * <p>
 * 结果经 FragmentResult 回传给 requestKey：{@link #isConfirmed(Bundle)}、{@link #getExtras(Bundle)}（透传的 Bundle）；
 * 点取消、返回键、点遮罩都回传未确认。不在对话框里保存回调对象，旋转与进程回收后由 FragmentManager 恢复。
 * <pre>
 * ConfirmDialogFragment.builder(REQ_DELETE)
 *         .title("删除这条用药记录？")
 *         .message("删除后不可恢复。")
 *         .positive("删除").negative("取消")
 *         .danger(true)
 *         .extras(extras)   // 例如 record_id，结果中原样带回
 *         .build()
 *         .showSafely(getSupportFragmentManager());
 *
 * getSupportFragmentManager().setFragmentResultListener(REQ_DELETE, this, (key, result) -&gt; {
 *     if (ConfirmDialogFragment.isConfirmed(result)) { ... }
 * });
 * </pre>
 * 在 Fragment 中用 {@code getChildFragmentManager()} 显示时，监听也注册在 {@code getChildFragmentManager()} 上。
 */
public class ConfirmDialogFragment extends BaseAlertDialogFragment {

    @NonNull
    public static Builder builder(@NonNull String requestKey) {
        return new Builder(requestKey);
    }

    /** “放弃已填写的内容？”确认（8.2 节）：按钮“继续填写 / 放弃”，确认即放弃 */
    @NonNull
    public static ConfirmDialogFragment discardChanges(@NonNull Context context, @NonNull String requestKey) {
        return builder(requestKey)
                .title(context.getString(R.string.base_discard_title))
                .message(context.getString(R.string.base_discard_message))
                .positive(context.getString(R.string.base_action_discard))
                .negative(context.getString(R.string.base_action_keep_editing))
                .build();
    }

    /** 对话框构建器 */
    public static final class Builder {
        private final Bundle args = new Bundle();

        Builder(@NonNull String requestKey) {
            args.putString(ARG_REQUEST_KEY, requestKey);
            args.putBoolean(ARG_CANCEL_ON_OUTSIDE, true);
        }

        @NonNull
        public Builder title(@Nullable CharSequence title) {
            args.putCharSequence(ARG_TITLE, title);
            return this;
        }

        @NonNull
        public Builder message(@Nullable CharSequence message) {
            args.putCharSequence(ARG_MESSAGE, message);
            return this;
        }

        /** 确认按钮（在右），写具体动作；不设时为“知道了” */
        @NonNull
        public Builder positive(@Nullable CharSequence text) {
            args.putCharSequence(ARG_POSITIVE, text);
            return this;
        }

        /** 次按钮（在左），为空时只显示一个按钮 */
        @NonNull
        public Builder negative(@Nullable CharSequence text) {
            args.putCharSequence(ARG_NEGATIVE, text);
            return this;
        }

        /** 危险操作：确认按钮 error 色 */
        @NonNull
        public Builder danger(boolean danger) {
            args.putBoolean(ARG_DANGER, danger);
            return this;
        }

        /** 顶部 24 dp 图标（secondary 色），有图标时图标与标题居中 */
        @NonNull
        public Builder icon(@DrawableRes int icon) {
            args.putInt(ARG_ICON, icon);
            return this;
        }

        /** 原样透传到结果中的数据 */
        @NonNull
        public Builder extras(@Nullable Bundle extras) {
            args.putBundle(ARG_EXTRAS, extras);
            return this;
        }

        /** 点遮罩是否关闭（默认 true，等同取消） */
        @NonNull
        public Builder cancelOnTouchOutside(boolean cancel) {
            args.putBoolean(ARG_CANCEL_ON_OUTSIDE, cancel);
            return this;
        }

        @NonNull
        public ConfirmDialogFragment build() {
            ConfirmDialogFragment f = new ConfirmDialogFragment();
            f.setArguments(new Bundle(args));
            return f;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        MaterialAlertDialogBuilder builder = newBuilder();
        CharSequence positive = args.getCharSequence(ARG_POSITIVE);
        builder.setPositiveButton(!TextUtils.isEmpty(positive) ? positive : getString(R.string.base_action_got_it),
                (d, which) -> sendResult(true));
        CharSequence negative = args.getCharSequence(ARG_NEGATIVE);
        if (!TextUtils.isEmpty(negative)) {
            builder.setNegativeButton(negative, (d, which) -> sendResult(false));
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(args.getBoolean(ARG_CANCEL_ON_OUTSIDE, true));
        return dialog;
    }
}
