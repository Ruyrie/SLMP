package com.zhimu.slmp.ai;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.AiCodes;
import com.zhimu.slmp.ai.ui.AiEntryActivity;
import com.zhimu.slmp.ai.ui.AssistantFragment;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.TabRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块11 AI 养殖助手与养殖课堂组件入口：创建 AiComponent；注册底部导航“助手”标签（AssistantFragment）。
 * K01 / K04 action 由 {@link AiEntryActivity} 带 RouterPath.EXTRA_MAIN_TAB 打开主页，切到“助手”并转交参数。
 */
public class AiModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        AiInjector.init(core);
        TabRegistry.register(TabRegistry.ASSISTANT, AssistantFragment::new);
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
        k.putLong(RouterPath.EXTRA_KNOWLEDGE_ID, 8101);
        k.putLong(RouterPath.EXTRA_CITATION_ID, 9902);
        pages.add(new DebugPage("K03 知识原文（休药期计算与出栏，带引用）", RouterPath.K03, k));
        pages.add(new DebugPage("K04 养殖课堂", RouterPath.K04));
        Bundle play = new Bundle();
        play.putLong(RouterPath.EXTRA_COURSE_ID, 6102);
        play.putString(RouterPath.EXTRA_ENTRY_TYPE, AiCodes.ENTRY_COURSE);
        pages.add(new DebugPage("K05 视频播放（仔猪腹泻的日常防控）", RouterPath.K05, play));
        Bundle topic = new Bundle();
        topic.putLong(RouterPath.EXTRA_TOPIC_ID, 7001);
        pages.add(new DebugPage("K07 专题详情（仔猪腹泻防控）", RouterPath.K07, topic));
        return pages;
    }
}
