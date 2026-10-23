package com.zhimu.slmp.ai;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.ui.AiEntryActivity;
import com.zhimu.slmp.ai.ui.AssistantFragment;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.MainTabHost;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块11 AI 养殖助手与养殖课堂组件入口：创建 AiComponent；注册底部导航“助手”标签（AssistantFragment）；
 * 注册生命周期回调——K01 / K04 action 经 {@link AiEntryActivity} 回到主页后，在实现 MainTabHost 的主页恢复时切到“助手”并转交参数。
 */
public class AiModuleEntry implements IModuleEntry {

    /** 等待主页恢复后转交的进入参数（主线程读写） */
    @Nullable
    private static Bundle pendingEntry;

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        AiInjector.init(core);
        TabRegistry.register(TabRegistry.ASSISTANT, AssistantFragment::new);
        app.registerActivityLifecycleCallbacks(new EntryCallbacks());
    }

    /** AiEntryActivity 调用：回到 MAIN 前暂存参数 */
    public static void postAssistantEntry(@NonNull Bundle args) {
        pendingEntry = args;
    }

    private static final class EntryCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            Bundle args = pendingEntry;
            if (args == null || !(activity instanceof MainTabHost) || activity instanceof AiEntryActivity) {
                return;
            }
            pendingEntry = null;
            ((MainTabHost) activity).selectTab(TabRegistry.ASSISTANT, args);
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("K01 AI 问答（助手页）", RouterPath.K01));
        Bundle hint = new Bundle();
        hint.putString(RouterPath.EXTRA_QUESTION, "保育猪换料后拉稀，要停料吗？");
        pages.add(new DebugPage("K01 带推荐问题（T01 入口）", RouterPath.K01, hint));
        pages.add(new DebugPage("K02 历史会话", RouterPath.K02));
        Bundle k = new Bundle();
        k.putLong(AiExtras.EXTRA_KNOWLEDGE_ID, 8101);
        k.putLong(AiExtras.EXTRA_CITATION_ID, 9902);
        pages.add(new DebugPage("K03 知识原文（休药期计算与出栏，带引用）", RouterPath.K03, k));
        pages.add(new DebugPage("K04 养殖课堂", RouterPath.K04));
        Bundle play = new Bundle();
        play.putLong(AiExtras.EXTRA_COURSE_ID, 6102);
        play.putString(AiExtras.EXTRA_ENTRY_TYPE, AiCodes.ENTRY_COURSE);
        pages.add(new DebugPage("K05 视频播放（仔猪腹泻的日常防控）", RouterPath.K05, play));
        Bundle topic = new Bundle();
        topic.putLong(AiExtras.EXTRA_TOPIC_ID, 7001);
        pages.add(new DebugPage("K07 专题详情（仔猪腹泻防控）", RouterPath.K07, topic));
        return pages;
    }
}
