package com.zhimu.slmp.health.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.health.ui.disinfectplan.DisinfectPlanListViewModel;
import com.zhimu.slmp.health.ui.disinfectplanedit.DisinfectPlanEditViewModel;
import com.zhimu.slmp.health.ui.disinfectregister.DisinfectRegisterViewModel;
import com.zhimu.slmp.health.ui.disinfecttask.DisinfectTaskViewModel;
import com.zhimu.slmp.health.ui.immuneitem.ImmuneItemEditViewModel;
import com.zhimu.slmp.health.ui.immuneplan.ImmunePlanViewModel;
import com.zhimu.slmp.health.ui.immuneregister.ImmuneRegisterViewModel;
import com.zhimu.slmp.health.ui.medicationarchive.MedicationArchiveViewModel;
import com.zhimu.slmp.health.ui.medicationregister.MedicationRegisterViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块08 免疫、用药与消毒组件的 ViewModel 多绑定（D01–D09）。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class HealthViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(ImmunePlanViewModel.class)
    abstract ViewModel bindImmunePlan(ImmunePlanViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ImmuneItemEditViewModel.class)
    abstract ViewModel bindImmuneItemEdit(ImmuneItemEditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ImmuneRegisterViewModel.class)
    abstract ViewModel bindImmuneRegister(ImmuneRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MedicationRegisterViewModel.class)
    abstract ViewModel bindMedicationRegister(MedicationRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MedicationArchiveViewModel.class)
    abstract ViewModel bindMedicationArchive(MedicationArchiveViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DisinfectPlanListViewModel.class)
    abstract ViewModel bindDisinfectPlanList(DisinfectPlanListViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DisinfectPlanEditViewModel.class)
    abstract ViewModel bindDisinfectPlanEdit(DisinfectPlanEditViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DisinfectRegisterViewModel.class)
    abstract ViewModel bindDisinfectRegister(DisinfectRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(DisinfectTaskViewModel.class)
    abstract ViewModel bindDisinfectTask(DisinfectTaskViewModel vm);
}
