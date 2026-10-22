package com.zhimu.slmp.common.ui.change;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.di.FeatureScope;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import retrofit2.Retrofit;

/**
 * U01、U02 的 Dagger 组件（dependencies = CoreComponent）：lib_common 中没有组件入口，首次打开页面时按需创建。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = {ChangeComponent.ChangeModule.class,
        ChangeComponent.ChangeViewModelModule.class})
public interface ChangeComponent {

    ViewModelFactory viewModelFactory();

    @Component.Factory
    interface Factory {
        ChangeComponent create(CoreComponent core);
    }

    @Module
    abstract class ChangeModule {
        @Provides
        @FeatureScope
        static ChangeApi changeApi(Retrofit retrofit) {
            return retrofit.create(ChangeApi.class);
        }
    }

    @Module(includes = ViewModelFactoryModule.class)
    abstract class ChangeViewModelModule {
        @Binds
        @IntoMap
        @ViewModelKey(ChangeRequestFormViewModel.class)
        abstract ViewModel bindForm(ChangeRequestFormViewModel vm);

        @Binds
        @IntoMap
        @ViewModelKey(MyChangeRequestsViewModel.class)
        abstract ViewModel bindList(MyChangeRequestsViewModel vm);
    }

    /** 取得组件（单例，首次调用时创建） */
    final class Injector {

        private static volatile ChangeComponent component;

        private Injector() {
        }

        @NonNull
        public static ChangeComponent get(@NonNull Context context) {
            ChangeComponent c = component;
            if (c == null) {
                synchronized (Injector.class) {
                    c = component;
                    if (c == null) {
                        c = DaggerChangeComponent.factory().create(CoreInjector.get(context));
                        component = c;
                    }
                }
            }
            return c;
        }

        @NonNull
        public static ViewModelFactory factory(@NonNull Context context) {
            return get(context).viewModelFactory();
        }
    }
}
