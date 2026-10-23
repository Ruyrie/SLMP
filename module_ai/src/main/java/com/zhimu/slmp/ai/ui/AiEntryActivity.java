package com.zhimu.slmp.ai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;

/**
 * K01、K04 的 action 入口（二者都是主页“助手”标签里的内容，不是独立页面）：
 * <ul>
 *     <li>壳工程中：把参数交给 {@link AiModuleEntry#postAssistantEntry}，以 CLEAR_TOP | SINGLE_TOP 回到 MAIN，
 *     MainActivity 恢复后由本组件注册的生命周期回调调用 {@code MainTabHost.selectTab(ASSISTANT, args)}；</li>
 *     <li>单独运行（没有 MAIN）时：本页直接承载 AssistantFragment。</li>
 * </ul>
 * 参数：K01 可带 RouterPath.EXTRA_QUESTION（T01 推荐问题）、AiExtras.EXTRA_COURSE_ID / EXTRA_COURSE_TITLE（K05“问 AI”）、
 * AiExtras.EXTRA_NEW_SESSION。
 */
public class AiEntryActivity extends BaseActivity implements MainTabHost {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = entryArgs(getIntent());
        if (savedInstanceState == null && Router.canOpen(this, RouterPath.MAIN)) {
            Bundle extras = new Bundle();
            extras.putString(RouterPath.EXTRA_MAIN_TAB, TabRegistry.ASSISTANT);
            extras.putBundle(RouterPath.EXTRA_MAIN_TAB_ARGS, args);
            Intent i = Router.intent(this, RouterPath.MAIN, extras)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.ai_entry_container);
        setContentView(container);
        if (savedInstanceState == null) {
            AssistantFragment f = new AssistantFragment();
            f.setArguments(args);
            getSupportFragmentManager().beginTransaction().replace(R.id.ai_entry_container, f).commit();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        getSupportFragmentManager().setFragmentResult(MainTabHost.argsResultKey(TabRegistry.ASSISTANT),
                entryArgs(intent));
    }

    /** 由 action 决定模式，其余参数原样带上 */
    @NonNull
    static Bundle entryArgs(@NonNull Intent intent) {
        Bundle b = intent.getExtras() == null ? new Bundle() : new Bundle(intent.getExtras());
        if (!b.containsKey(AiExtras.EXTRA_ASSISTANT_MODE)) {
            b.putString(AiExtras.EXTRA_ASSISTANT_MODE,
                    RouterPath.K04.equals(intent.getAction()) ? AiExtras.MODE_COURSE : AiExtras.MODE_CHAT);
        }
        if (RouterPath.K01.equals(intent.getAction()) && !b.containsKey(AiExtras.EXTRA_NEW_SESSION)
                && (b.containsKey(RouterPath.EXTRA_QUESTION) || b.containsKey(AiExtras.EXTRA_COURSE_ID))) {
            b.putBoolean(AiExtras.EXTRA_NEW_SESSION, true);
        }
        return b;
    }

    @Override
    public void selectTab(@NonNull String tabKey) {
        showMessage("单独运行时没有底部导航：" + tabKey);
    }

    @Override
    public void selectTab(@NonNull String tabKey, @Nullable Bundle args) {
        if (TabRegistry.ASSISTANT.equals(tabKey) && args != null) {
            getSupportFragmentManager().setFragmentResult(MainTabHost.argsResultKey(tabKey), args);
        } else {
            selectTab(tabKey);
        }
    }
}
