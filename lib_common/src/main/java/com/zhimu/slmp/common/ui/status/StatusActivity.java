package com.zhimu.slmp.common.ui.status;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StateLayout;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.router.RouterPath;

/**
 * U03 通用状态页（02 号《UI设计规范》第十一节 U03）：整页显示“加载失败”或“无权查看”。
 * <p>
 * 通常 U03 直接在目标页内容区用 StateLayout 显示（顶部栏保留目标页标题）；本页用于目标页无法打开时的整页提示
 * （如推送、扫码进入的页面在加载前就判定失败或无权）。
 * <ul>
 *     <li>加载失败（{@link #TYPE_ERROR}）：{@code cloud_off} + 标题 + 原因 + 填充按钮“重新加载”+ trace_id；
 *     点“重新加载”以 {@link Activity#RESULT_OK} 关闭，由来源页重新请求</li>
 *     <li>无权查看（{@link #TYPE_NO_PERMISSION}）：{@code lock} + 标题 + 原因和找谁 + 色调按钮“返回 + 来源页”，点按钮或返回键关闭</li>
 * </ul>
 * Intent extras：{@link RouterPath#EXTRA_STATUS_TYPE}（ERROR / NO_PERMISSION）、{@link RouterPath#EXTRA_TITLE}（状态标题）、
 * {@link #EXTRA_MESSAGE}（说明，也可用 {@link RouterPath#EXTRA_REASON}）、{@link RouterPath#EXTRA_TRACE_ID}；
 * 可选 {@link #EXTRA_PAGE_TITLE}（顶部栏标题，保留目标页标题）与 {@link #EXTRA_ACTION_TEXT}（无权查看按钮文字，如“返回消息中心”）。
 * 用 {@link #intent} 组装，或 {@code Router.open(context, RouterPath.U03, extras)}。
 */
public class StatusActivity extends BaseActivity {

    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_NO_PERMISSION = "NO_PERMISSION";

    /** 说明文字（String） */
    public static final String EXTRA_MESSAGE = RouterPath.EXTRA_MESSAGE;
    /** 顶部栏标题（String），保留目标页的标题 */
    public static final String EXTRA_PAGE_TITLE = RouterPath.EXTRA_PAGE_TITLE;
    /** 无权查看按钮文字（String），写“返回 + 来源页” */
    public static final String EXTRA_ACTION_TEXT = RouterPath.EXTRA_ACTION_TEXT;
    /** 加载失败时的错误码（int，-1 为网络不可用），用于选择默认说明 */
    public static final String EXTRA_ERROR_CODE = RouterPath.EXTRA_ERROR_CODE;

    /**
     * 组装打开 U03 的 Intent。
     *
     * @param type    {@link #TYPE_ERROR} 或 {@link #TYPE_NO_PERMISSION}
     * @param title   状态标题（“待办加载失败”“你没有这个栏舍的查看权限”）
     * @param message 说明
     * @param traceId 加载失败的 trace_id，可为空
     */
    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String type, @Nullable String title,
                                @Nullable String message, @Nullable String traceId) {
        Intent intent = new Intent(context, StatusActivity.class);
        intent.putExtra(RouterPath.EXTRA_STATUS_TYPE, type);
        intent.putExtra(RouterPath.EXTRA_TITLE, title);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(RouterPath.EXTRA_TRACE_ID, traceId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.common_ui_activity_status);
        Intent intent = getIntent();
        String type = intent.getStringExtra(RouterPath.EXTRA_STATUS_TYPE);
        String title = intent.getStringExtra(RouterPath.EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if (TextUtils.isEmpty(message)) {
            message = intent.getStringExtra(RouterPath.EXTRA_REASON);
        }
        String traceId = intent.getStringExtra(RouterPath.EXTRA_TRACE_ID);

        MaterialToolbar toolbar = findViewById(com.zhimu.slmp.base.R.id.base_toolbar);
        TopAppBarHelper.setupBack(toolbar, intent.getStringExtra(EXTRA_PAGE_TITLE));

        StateLayout state = findViewById(R.id.common_ui_status_state);
        if (TYPE_NO_PERMISSION.equals(type)) {
            String actionText = intent.getStringExtra(EXTRA_ACTION_TEXT);
            if (!TextUtils.isEmpty(actionText)) {
                state.setNoPermissionActionText(actionText);
            }
            state.setOnNoPermissionActionListener(v -> finish());
            state.showNoPermission(!TextUtils.isEmpty(title) ? title
                    : getString(R.string.common_ui_status_no_permission_title), message);
        } else {
            state.setOnRetryListener(v -> {
                setResult(Activity.RESULT_OK);
                finish();
            });
            state.showError(!TextUtils.isEmpty(title) ? title : getString(R.string.common_ui_status_error_title),
                    message, traceId, intent.getIntExtra(EXTRA_ERROR_CODE, 0));
        }
    }
}
