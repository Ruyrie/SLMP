package com.zhimu.slmp.base.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.UiMessage;

/**
 * Activity 基类：edge-to-edge 与系统栏 insets、点击输入框以外区域收起软键盘、
 * 观察 ViewModel 的 message 并用 Snackbar 显示（第 1 级弹窗）。
 * 登录失效、全局提示、需要更新由 BaseApplication 的 ActivityLifecycleCallbacks 统一处理，这里不感知。
 */
public abstract class BaseActivity extends AppCompatActivity {

    private boolean insetsApplied;

    /**
     * 进入页面后显示一次的提示（String），例如跨养殖场打开时的“已切换到西岭鸡场”；
     * 与 RouterPath.EXTRA_SWITCH_TIP 的值相同。
     */
    public static final String EXTRA_ENTRY_TIP = "switch_tip";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            showEntryTip(getIntent());
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        showEntryTip(intent);
    }

    /** 页面布局完成后显示 EXTRA_ENTRY_TIP，并从 Intent 中移除，避免重建后重复显示 */
    private void showEntryTip(@Nullable Intent intent) {
        String tip = intent == null ? null : intent.getStringExtra(EXTRA_ENTRY_TIP);
        if (tip == null || tip.isEmpty()) {
            return;
        }
        intent.removeExtra(EXTRA_ENTRY_TIP);
        getWindow().getDecorView().post(() -> {
            if (!isFinishing()) {
                showMessage(tip);
            }
        });
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (insetsApplied || !applyWindowInsetsToRoot()) {
            return;
        }
        ViewGroup content = findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            insetsApplied = true;
            InsetsHelper.applySystemBarsPadding(content.getChildAt(0), true, true);
        }
    }

    /**
     * 是否把系统栏（与软键盘）insets 作为内边距加到布局根视图上；
     * 顶部栏需要延伸到状态栏下、或自己处理 insets 的页面返回 false 后调用 InsetsHelper。
     */
    protected boolean applyWindowInsetsToRoot() {
        return true;
    }

    // ===== ViewModel =====

    protected <T extends ViewModel> T obtainViewModel(@NonNull ViewModelProvider.Factory factory,
                                                      @NonNull Class<T> cls) {
        return new ViewModelProvider(this, factory).get(cls);
    }

    /** 观察 BaseViewModel 的一次性提示 */
    protected void observeBase(@NonNull BaseViewModel vm) {
        vm.getMessage().observe(this, this::showMessage);
    }

    /** 显示第 1 级提示 */
    public void showMessage(@Nullable UiMessage message) {
        if (message == null) {
            return;
        }
        View content = findViewById(android.R.id.content);
        if (content != null) {
            SnackbarHelper.show(content, message, getSnackbarAnchor(), id -> dispatchMessageAction(message, id));
        }
    }

    public void showMessage(@NonNull String text) {
        showMessage(UiMessage.of(text));
    }

    /** Snackbar 操作被点击（UiMessage.withAction 的 actionId；UiMessage.withPage 的页面操作由基类处理，不会回调） */
    protected void onMessageAction(@NonNull String actionId) {
    }

    private void dispatchMessageAction(@NonNull UiMessage message, @NonNull String actionId) {
        if (UiMessage.isPageAction(actionId)) {
            openPageAction(this, actionId, message.getActionExtras());
        } else {
            onMessageAction(actionId);
        }
    }

    /** 打开页面 action（隐式 Intent 限定本应用包名）；页面不存在时提示“该功能正在开发中” */
    static void openPageAction(@NonNull Activity activity, @NonNull String action, @Nullable Bundle extras) {
        Intent intent = new Intent(action).setPackage(activity.getPackageName());
        if (extras != null) {
            intent.putExtras(extras);
        }
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            SnackbarHelper.show(activity, activity.getString(R.string.base_page_unavailable));
            return;
        }
        activity.startActivity(intent);
    }

    /** Snackbar 锚点：默认取布局中带 SnackbarHelper.ANCHOR_TAG 标签的可见视图 */
    @Nullable
    public View getSnackbarAnchor() {
        return SnackbarHelper.findAnchor(findViewById(android.R.id.content));
    }

    // ===== 软键盘 =====

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus instanceof EditText && !isTouchInside(focus, ev)) {
                hideKeyboard(focus);
                focus.clearFocus();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private static boolean isTouchInside(View view, MotionEvent ev) {
        Rect rect = new Rect();
        view.getGlobalVisibleRect(rect);
        return rect.contains((int) ev.getRawX(), (int) ev.getRawY());
    }

    protected void hideKeyboard(@Nullable View view) {
        View target = view != null ? view : getCurrentFocus();
        if (target == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }
}
