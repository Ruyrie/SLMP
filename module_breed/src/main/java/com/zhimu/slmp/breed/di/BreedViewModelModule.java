package com.zhimu.slmp.breed.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.breed.ui.archive.AnimalArchiveViewModel;
import com.zhimu.slmp.breed.ui.archive.AnimalFormViewModel;
import com.zhimu.slmp.breed.ui.archive.ChangeLogViewModel;
import com.zhimu.slmp.breed.ui.due.BreedDueViewModel;
import com.zhimu.slmp.breed.ui.farrowing.FarrowingRegisterViewModel;
import com.zhimu.slmp.breed.ui.home.BreedHomeViewModel;
import com.zhimu.slmp.breed.ui.incubation.IncubationViewModel;
import com.zhimu.slmp.breed.ui.mating.MatingRegisterViewModel;
import com.zhimu.slmp.breed.ui.weaning.WeaningRegisterViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 模块05 繁育管理组件的 ViewModel 多绑定。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class BreedViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(MatingRegisterViewModel.class)
    abstract ViewModel bindMating(MatingRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(FarrowingRegisterViewModel.class)
    abstract ViewModel bindFarrowing(FarrowingRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(WeaningRegisterViewModel.class)
    abstract ViewModel bindWeaning(WeaningRegisterViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AnimalArchiveViewModel.class)
    abstract ViewModel bindArchive(AnimalArchiveViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(ChangeLogViewModel.class)
    abstract ViewModel bindChangeLog(ChangeLogViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(AnimalFormViewModel.class)
    abstract ViewModel bindAnimalForm(AnimalFormViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BreedDueViewModel.class)
    abstract ViewModel bindDue(BreedDueViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(BreedHomeViewModel.class)
    abstract ViewModel bindHome(BreedHomeViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(IncubationViewModel.class)
    abstract ViewModel bindIncubation(IncubationViewModel vm);
}
