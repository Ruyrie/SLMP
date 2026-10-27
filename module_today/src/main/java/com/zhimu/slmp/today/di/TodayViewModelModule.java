package com.zhimu.slmp.today.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.today.ui.calendar.CalendarViewModel;
import com.zhimu.slmp.today.ui.detail.TodoDetailViewModel;
import com.zhimu.slmp.today.ui.home.TodayViewModel;
import com.zhimu.slmp.today.ui.invalid.RecordInvalidViewModel;
import com.zhimu.slmp.today.ui.message.MessageCenterViewModel;
import com.zhimu.slmp.today.ui.notice.NoticeDetailViewModel;
import com.zhimu.slmp.today.ui.push.PushRouterViewModel;
import com.zhimu.slmp.today.ui.setting.ReminderSettingViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块03 今日首页、日历待办与消息中心组件的 ViewModel 多绑定。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class TodayViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(TodayViewModel.class)
    abstract ViewModel bindToday(TodayViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(com.zhimu.slmp.today.ui.collection.TodoCollectionViewModel.class)
    abstract ViewModel bindCollection(com.zhimu.slmp.today.ui.collection.TodoCollectionViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(CalendarViewModel.class)
    abstract ViewModel bindCalendar(CalendarViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(TodoDetailViewModel.class)
    abstract ViewModel bindTodoDetail(TodoDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ReminderSettingViewModel.class)
    abstract ViewModel bindReminderSetting(ReminderSettingViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MessageCenterViewModel.class)
    abstract ViewModel bindMessageCenter(MessageCenterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(NoticeDetailViewModel.class)
    abstract ViewModel bindNoticeDetail(NoticeDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(RecordInvalidViewModel.class)
    abstract ViewModel bindRecordInvalid(RecordInvalidViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(PushRouterViewModel.class)
    abstract ViewModel bindPushRouter(PushRouterViewModel vm);
}
