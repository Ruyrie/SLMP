package com.zhimu.slmp.base.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.util.ClickThrottle;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面状态容器（02 号《UI设计规范》7.19、8.2、9.6 节与 U03）：包裹内容区，按 {@link UiState} 切换
 * 内容 / 骨架屏 / 空状态 / 加载失败 / 无权查看。只替换内容区，顶部栏、页内筛选和视图切换放在本控件外面，保持可用。
 * <ul>
 *     <li>LOADING：显示骨架屏（{@code app:stateSkeletonLayout}，默认 {@code base_skeleton_list}）；
 *     在加载失败状态下点“重新加载”后，保持失败版式，只在按钮内显示环形进度</li>
 *     <li>CONTENT：显示内容，从骨架或失败状态切换时淡入 200 ms</li>
 *     <li>EMPTY：图标 + “还没有 + 对象” + 说明 + 色调按钮（直达下一步）；筛选后为空用文字按钮“查看全部”</li>
 *     <li>ERROR：{@code cloud_off} + “对象 + 加载失败” + 原因 + 填充按钮“重新加载”（{@code refresh}）+ trace_id 小字</li>
 *     <li>NO_PERMISSION：{@code lock} + “你没有这个 + 对象 + 的查看权限” + 原因和找谁 + 色调按钮“返回 + 来源页”</li>
 * </ul>
 * <pre>
 * &lt;com.zhimu.slmp.base.widget.StateLayout
 *     android:id="@+id/state_layout"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:stateEmptyIcon="@drawable/ic_edit_note"
 *     app:stateEmptyTitle="还没有修改申请"
 *     app:stateEmptyMessage="需要修改存栏、成本、库存等关键记录时……"
 *     app:stateEmptyActionText="去栏舍与存栏"
 *     app:stateEmptyActionIcon="@drawable/ic_fence"
 *     app:stateErrorTitle="修改申请加载失败"
 *     app:stateNoPermissionActionText="返回消息中心"&gt;
 *     &lt;androidx.recyclerview.widget.RecyclerView ... /&gt;
 * &lt;/com.zhimu.slmp.base.widget.StateLayout&gt;
 *
 * viewModel.getUiState().observe(this, binding.stateLayout::render);
 * binding.stateLayout.setOnRetryListener(v -&gt; viewModel.reload());
 * binding.stateLayout.setOnEmptyActionListener(v -&gt; Router.open(this, RouterPath.L03));
 * </pre>
 * 无权查看的按钮默认执行返回（OnBackPressedDispatcher），可用 {@link #setOnNoPermissionActionListener} 改为其他动作。
 * “仍然无法加载，请稍后再试”的 Snackbar 由页面在第二次失败时显示。
 */
public class StateLayout extends FrameLayout {

    public static final int EMPTY_ACTION_TONAL = 0;
    public static final int EMPTY_ACTION_TEXT = 1;

    private static final long FADE_IN_MS = 200;

    private final List<View> contentViews = new ArrayList<>();
    @Nullable
    private View skeletonView;
    @Nullable
    private View stateView;

    @LayoutRes
    private int skeletonLayout;
    @DrawableRes
    private int emptyIcon;
    @Nullable
    private CharSequence emptyTitle;
    @Nullable
    private CharSequence emptyMessage;
    @Nullable
    private CharSequence emptyActionText;
    @DrawableRes
    private int emptyActionIcon;
    private int emptyActionStyle = EMPTY_ACTION_TONAL;
    @Nullable
    private CharSequence errorTitle;
    @Nullable
    private CharSequence noPermissionTitle;
    @Nullable
    private CharSequence noPermissionActionText;

    @Nullable
    private OnClickListener retryListener;
    @Nullable
    private OnClickListener emptyActionListener;
    @Nullable
    private OnClickListener noPermissionActionListener;

    @NonNull
    private UiState.Status status = UiState.Status.CONTENT;
    private boolean retrying;
    private int initialState;

    public StateLayout(@NonNull Context context) {
        this(context, null);
    }

    public StateLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        skeletonLayout = R.layout.base_skeleton_list;
        emptyIcon = R.drawable.ic_info;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StateLayout);
            skeletonLayout = a.getResourceId(R.styleable.StateLayout_stateSkeletonLayout, skeletonLayout);
            emptyIcon = a.getResourceId(R.styleable.StateLayout_stateEmptyIcon, emptyIcon);
            emptyTitle = a.getText(R.styleable.StateLayout_stateEmptyTitle);
            emptyMessage = a.getText(R.styleable.StateLayout_stateEmptyMessage);
            emptyActionText = a.getText(R.styleable.StateLayout_stateEmptyActionText);
            emptyActionIcon = a.getResourceId(R.styleable.StateLayout_stateEmptyActionIcon, 0);
            emptyActionStyle = a.getInt(R.styleable.StateLayout_stateEmptyActionStyle, EMPTY_ACTION_TONAL);
            errorTitle = a.getText(R.styleable.StateLayout_stateErrorTitle);
            noPermissionTitle = a.getText(R.styleable.StateLayout_stateNoPermissionTitle);
            noPermissionActionText = a.getText(R.styleable.StateLayout_stateNoPermissionActionText);
            initialState = a.getInt(R.styleable.StateLayout_stateInitial, 0);
            a.recycle();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        contentViews.clear();
        for (int i = 0; i < getChildCount(); i++) {
            contentViews.add(getChildAt(i));
        }
        if (initialState == 1) {
            showLoading();
        } else if (initialState == 2) {
            showEmpty(null, null);
        }
    }

    // ========================= 渲染 =========================

    /** 按页面状态切换显示；ViewModel 的 uiState 直接交给它 */
    public void render(@Nullable UiState state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                showLoading();
                break;
            case EMPTY:
                showEmpty(state.getTitle(), state.getMessage());
                break;
            case ERROR:
                showError(state.getTitle(), state.getMessage(), state.getTraceId(), state.getErrorCode());
                break;
            case NO_PERMISSION:
                showNoPermission(state.getTitle(), state.getMessage());
                break;
            case CONTENT:
            default:
                showContent();
                break;
        }
    }

    @NonNull
    public UiState.Status getStatus() {
        return status;
    }

    public void showContent() {
        boolean fade = status != UiState.Status.CONTENT;
        status = UiState.Status.CONTENT;
        stopRetrying();
        setVisible(skeletonView, false);
        setVisible(stateView, false);
        for (View v : contentViews) {
            v.setVisibility(VISIBLE);
            if (fade) {
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(FADE_IN_MS).start();
            }
        }
    }

    /** 首次加载：骨架屏。失败后点“重新加载”期间保持失败版式，只在按钮内显示进度 */
    public void showLoading() {
        if (retrying && status == UiState.Status.ERROR) {
            return;
        }
        status = UiState.Status.LOADING;
        setContentVisible(false);
        setVisible(stateView, false);
        ensureSkeleton();
        setVisible(skeletonView, true);
    }

    /**
     * 空状态。
     *
     * @param title   为空时用 {@code app:stateEmptyTitle}
     * @param message 为空时用 {@code app:stateEmptyMessage}
     */
    public void showEmpty(@Nullable CharSequence title, @Nullable CharSequence message) {
        status = UiState.Status.EMPTY;
        stopRetrying();
        StateViews v = prepareStateView();
        v.icon.setImageResource(emptyIcon);
        v.title.setText(!TextUtils.isEmpty(title) ? title
                : !TextUtils.isEmpty(emptyTitle) ? emptyTitle : getContext().getString(R.string.base_state_empty_title));
        setTextOrGone(v.message, !TextUtils.isEmpty(message) ? message : emptyMessage);
        v.trace.setVisibility(GONE);
        MaterialButton button = emptyActionStyle == EMPTY_ACTION_TEXT ? v.text : v.tonal;
        showButton(v, TextUtils.isEmpty(emptyActionText) ? null : button, emptyActionText, emptyActionIcon,
                emptyActionListener);
    }

    /**
     * 加载失败（U03 加载失败版式）。
     *
     * @param title     为空时用 {@code app:stateErrorTitle}，再为空用“内容加载失败”
     * @param message   为空时按错误码给出网络或服务异常的默认说明
     * @param traceId   服务端 trace_id，显示为小字便于反馈
     * @param errorCode 错误码（{@link ErrorCodes#NETWORK} 为网络不可用）
     */
    public void showError(@Nullable CharSequence title, @Nullable CharSequence message, @Nullable String traceId,
                          int errorCode) {
        status = UiState.Status.ERROR;
        stopRetrying();
        StateViews v = prepareStateView();
        v.icon.setImageResource(R.drawable.ic_cloud_off);
        v.title.setText(!TextUtils.isEmpty(title) ? title
                : !TextUtils.isEmpty(errorTitle) ? errorTitle : getContext().getString(R.string.base_state_error_title));
        CharSequence msg = message;
        if (TextUtils.isEmpty(msg)) {
            msg = getContext().getString(errorCode == ErrorCodes.NETWORK
                    ? R.string.base_state_error_message_network : R.string.base_state_error_message_server);
        }
        setTextOrGone(v.message, msg);
        if (TextUtils.isEmpty(traceId)) {
            v.trace.setVisibility(GONE);
        } else {
            v.trace.setText(getContext().getString(R.string.base_state_trace_id, traceId));
            v.trace.setVisibility(VISIBLE);
        }
        showButton(v, v.filled, getContext().getString(R.string.base_action_retry), R.drawable.ic_refresh, view -> {
            if (retryListener != null) {
                retrying = true;
                ButtonLoading.setLoading(v.filled, true);
                retryListener.onClick(view);
            }
        });
    }

    public void showError(@Nullable CharSequence message, @Nullable String traceId) {
        showError(null, message, traceId, 0);
    }

    /**
     * 无权查看（U03 无权查看版式）。
     *
     * @param title   为空时用 {@code app:stateNoPermissionTitle}，再为空用“你没有这项内容的查看权限”
     * @param message 原因和可以找谁；为空时用“如需查看，请联系所有者或场长。”
     */
    public void showNoPermission(@Nullable CharSequence title, @Nullable CharSequence message) {
        status = UiState.Status.NO_PERMISSION;
        stopRetrying();
        StateViews v = prepareStateView();
        v.icon.setImageResource(R.drawable.ic_lock);
        v.title.setText(!TextUtils.isEmpty(title) ? title
                : !TextUtils.isEmpty(noPermissionTitle) ? noPermissionTitle
                : getContext().getString(R.string.base_state_no_permission_title));
        setTextOrGone(v.message, !TextUtils.isEmpty(message) ? message
                : getContext().getString(R.string.base_state_no_permission_message));
        v.trace.setVisibility(GONE);
        CharSequence actionText = !TextUtils.isEmpty(noPermissionActionText) ? noPermissionActionText
                : getContext().getString(R.string.base_action_back);
        showButton(v, v.tonal, actionText, 0, view -> {
            if (noPermissionActionListener != null) {
                noPermissionActionListener.onClick(view);
            } else {
                Activity activity = findActivity(getContext());
                if (activity instanceof ComponentActivity) {
                    ((ComponentActivity) activity).getOnBackPressedDispatcher().onBackPressed();
                } else if (activity != null) {
                    activity.finish();
                }
            }
        });
    }

    // ========================= 配置 =========================

    /** “重新加载”点击：只重新请求内容区数据；点击后按钮显示进度，直到下一次 render */
    public void setOnRetryListener(@Nullable OnClickListener listener) {
        retryListener = listener;
    }

    public void setOnEmptyActionListener(@Nullable OnClickListener listener) {
        emptyActionListener = listener;
        if (status == UiState.Status.EMPTY && stateView != null) {
            showEmpty(stateViews().title.getText(), stateViews().message.getText());
        }
    }

    public void setOnNoPermissionActionListener(@Nullable OnClickListener listener) {
        noPermissionActionListener = listener;
    }

    /** 骨架屏布局（与真实布局同结构）；0 为默认的分组列表骨架 */
    public void setSkeletonLayout(@LayoutRes int layoutRes) {
        int res = layoutRes == 0 ? R.layout.base_skeleton_list : layoutRes;
        if (res != skeletonLayout) {
            skeletonLayout = res;
            if (skeletonView != null) {
                removeView(skeletonView);
                skeletonView = null;
            }
            if (status == UiState.Status.LOADING) {
                showLoading();
            }
        }
    }

    public void setEmptyIcon(@DrawableRes int iconRes) {
        emptyIcon = iconRes;
    }

    public void setEmptyTitle(@Nullable CharSequence title) {
        emptyTitle = title;
    }

    public void setEmptyMessage(@Nullable CharSequence message) {
        emptyMessage = message;
    }

    /**
     * 空状态按钮。
     *
     * @param style {@link #EMPTY_ACTION_TONAL}（下一步）或 {@link #EMPTY_ACTION_TEXT}（筛选后为空的“查看全部”）
     */
    public void setEmptyAction(@Nullable CharSequence text, @DrawableRes int iconRes, int style,
                               @Nullable OnClickListener listener) {
        emptyActionText = text;
        emptyActionIcon = iconRes;
        emptyActionStyle = style;
        emptyActionListener = listener;
    }

    /** 一次设置空状态的全部参数（同一页面筛选前后空状态不同时使用），随后调用 render / showEmpty 生效 */
    public void setEmpty(@DrawableRes int iconRes, @Nullable CharSequence title, @Nullable CharSequence message,
                         @Nullable CharSequence actionText, @DrawableRes int actionIcon, int actionStyle,
                         @Nullable OnClickListener listener) {
        emptyIcon = iconRes;
        emptyTitle = title;
        emptyMessage = message;
        setEmptyAction(actionText, actionIcon, actionStyle, listener);
    }

    /** 加载失败标题，写“对象 + 加载失败” */
    public void setErrorTitle(@Nullable CharSequence title) {
        errorTitle = title;
    }

    /** 无权查看标题，写“你没有这个 + 对象 + 的查看权限” */
    public void setNoPermissionTitle(@Nullable CharSequence title) {
        noPermissionTitle = title;
    }

    /** 无权查看按钮文字，写“返回 + 来源页” */
    public void setNoPermissionActionText(@Nullable CharSequence text) {
        noPermissionActionText = text;
    }

    // ========================= 内部 =========================

    private void ensureSkeleton() {
        if (skeletonView == null) {
            skeletonView = LayoutInflater.from(getContext()).inflate(skeletonLayout, this, false);
            addView(skeletonView);
        }
    }

    @NonNull
    private StateViews prepareStateView() {
        if (stateView == null) {
            stateView = LayoutInflater.from(getContext()).inflate(R.layout.base_view_state, this, false);
            stateView.setTag(R.id.base_state_root, new StateViews(stateView));
            addView(stateView);
        }
        setContentVisible(false);
        setVisible(skeletonView, false);
        setVisible(stateView, true);
        return stateViews();
    }

    @NonNull
    private StateViews stateViews() {
        return (StateViews) stateView.getTag(R.id.base_state_root);
    }

    private void showButton(@NonNull StateViews v, @Nullable MaterialButton shown, @Nullable CharSequence text,
                            @DrawableRes int iconRes, @Nullable OnClickListener listener) {
        for (MaterialButton b : new MaterialButton[]{v.filled, v.tonal, v.text}) {
            b.setVisibility(b == shown ? VISIBLE : GONE);
        }
        if (shown == null) {
            return;
        }
        shown.setText(text);
        if (iconRes != 0) {
            shown.setIconResource(iconRes);
        } else {
            shown.setIcon(null);
        }
        ClickThrottle.setOnClickListener(shown, listener);
    }

    private void stopRetrying() {
        retrying = false;
        if (stateView != null) {
            ButtonLoading.setLoading(stateViews().filled, false);
        }
    }

    private void setContentVisible(boolean visible) {
        for (View v : contentViews) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private static void setVisible(@Nullable View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private static void setTextOrGone(@NonNull TextView view, @Nullable CharSequence text) {
        view.setText(text);
        view.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /** 状态占位中的视图 */
    private static final class StateViews {
        final ImageView icon;
        final TextView title;
        final TextView message;
        final TextView trace;
        final MaterialButton filled;
        final MaterialButton tonal;
        final MaterialButton text;

        StateViews(@NonNull View root) {
            icon = root.findViewById(R.id.base_state_icon);
            title = root.findViewById(R.id.base_state_title);
            message = root.findViewById(R.id.base_state_message);
            trace = root.findViewById(R.id.base_state_trace);
            filled = root.findViewById(R.id.base_state_button_filled);
            tonal = root.findViewById(R.id.base_state_button_tonal);
            text = root.findViewById(R.id.base_state_button_text);
        }
    }
}
