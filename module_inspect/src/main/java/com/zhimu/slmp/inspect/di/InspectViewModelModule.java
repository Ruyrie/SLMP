package com.zhimu.slmp.inspect.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.inspect.ui.backfill.InspectBackfillViewModel;
import com.zhimu.slmp.inspect.ui.diagnosis.DiagnosisDetailViewModel;
import com.zhimu.slmp.inspect.ui.handle.HandleResultViewModel;
import com.zhimu.slmp.inspect.ui.history.InspectHistoryViewModel;
import com.zhimu.slmp.inspect.ui.register.InspectRegisterViewModel;
import com.zhimu.slmp.inspect.ui.report.AbnormalReportViewModel;
import com.zhimu.slmp.inspect.ui.today.InspectTodayViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块07 日常巡检与异常上报 AI 诊断组件的 ViewModel 多绑定（I01–I07；I08、I09 在 lib_media）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class InspectViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(InspectTodayViewModel.class)
    abstract ViewModel bindToday(InspectTodayViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(InspectRegisterViewModel.class)
    abstract ViewModel bindRegister(InspectRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(InspectBackfillViewModel.class)
    abstract ViewModel bindBackfill(InspectBackfillViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(InspectHistoryViewModel.class)
    abstract ViewModel bindHistory(InspectHistoryViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AbnormalReportViewModel.class)
    abstract ViewModel bindReport(AbnormalReportViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DiagnosisDetailViewModel.class)
    abstract ViewModel bindDiagnosis(DiagnosisDetailViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(HandleResultViewModel.class)
    abstract ViewModel bindHandle(HandleResultViewModel vm);
}
