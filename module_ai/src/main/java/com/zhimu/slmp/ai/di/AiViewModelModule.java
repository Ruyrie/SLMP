package com.zhimu.slmp.ai.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.ai.ui.chat.AiChatViewModel;
import com.zhimu.slmp.ai.ui.course.CourseHomeViewModel;
import com.zhimu.slmp.ai.ui.history.WatchHistoryViewModel;
import com.zhimu.slmp.ai.ui.knowledge.KnowledgeDetailViewModel;
import com.zhimu.slmp.ai.ui.play.CoursePlayViewModel;
import com.zhimu.slmp.ai.ui.session.AiSessionListViewModel;
import com.zhimu.slmp.ai.ui.topic.TopicDetailViewModel;
import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块11 AI 养殖助手与养殖课堂组件的 ViewModel 多绑定（K01–K07 与观看记录）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class AiViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(AiChatViewModel.class)
    abstract ViewModel bindChat(AiChatViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AiSessionListViewModel.class)
    abstract ViewModel bindSessions(AiSessionListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(KnowledgeDetailViewModel.class)
    abstract ViewModel bindKnowledge(KnowledgeDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(CourseHomeViewModel.class)
    abstract ViewModel bindCourseHome(CourseHomeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(WatchHistoryViewModel.class)
    abstract ViewModel bindWatchHistory(WatchHistoryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(CoursePlayViewModel.class)
    abstract ViewModel bindCoursePlay(CoursePlayViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(TopicDetailViewModel.class)
    abstract ViewModel bindTopic(TopicDetailViewModel vm);
}
