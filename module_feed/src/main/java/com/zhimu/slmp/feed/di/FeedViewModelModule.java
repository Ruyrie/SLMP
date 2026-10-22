package com.zhimu.slmp.feed.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.feed.ui.exec.FeedExecViewModel;
import com.zhimu.slmp.feed.ui.growth.GrowthCurveViewModel;
import com.zhimu.slmp.feed.ui.plan.FeedPlanViewModel;
import com.zhimu.slmp.feed.ui.register.FeedRegisterViewModel;
import com.zhimu.slmp.feed.ui.today.FeedTodayViewModel;
import com.zhimu.slmp.feed.ui.weigh.WeighRegisterViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块06 饲喂与生长称重组件的 ViewModel 多绑定（G01–G06）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class FeedViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(FeedTodayViewModel.class)
    abstract ViewModel bindFeedToday(FeedTodayViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedRegisterViewModel.class)
    abstract ViewModel bindFeedRegister(FeedRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedPlanViewModel.class)
    abstract ViewModel bindFeedPlan(FeedPlanViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FeedExecViewModel.class)
    abstract ViewModel bindFeedExec(FeedExecViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(WeighRegisterViewModel.class)
    abstract ViewModel bindWeighRegister(WeighRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(GrowthCurveViewModel.class)
    abstract ViewModel bindGrowthCurve(GrowthCurveViewModel vm);
}
