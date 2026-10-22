package com.zhimu.slmp.base.mvvm;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Dagger 多绑定的 ViewModel 工厂：按类型取出 Provider 创建 ViewModel，
 * 并把 SavedStateHandle（含 Intent extras / Fragment arguments）交给 BaseViewModel。
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final Map<Class<? extends ViewModel>, Provider<ViewModel>> creators;

    @Inject
    public ViewModelFactory(Map<Class<? extends ViewModel>, Provider<ViewModel>> creators) {
        this.creators = creators;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
        T vm = instantiate(modelClass);
        if (vm instanceof BaseViewModel) {
            SavedStateHandle handle;
            try {
                handle = SavedStateHandleSupport.createSavedStateHandle(extras);
            } catch (IllegalArgumentException e) {
                // 所有者没有提供 SavedStateRegistryOwner（例如手动 new ViewModelProvider(store, factory)）
                handle = new SavedStateHandle();
            }
            ((BaseViewModel) vm).attachSavedState(handle);
        }
        return vm;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        T vm = instantiate(modelClass);
        if (vm instanceof BaseViewModel) {
            ((BaseViewModel) vm).attachSavedState(new SavedStateHandle());
        }
        return vm;
    }

    @SuppressWarnings("unchecked")
    private <T extends ViewModel> T instantiate(Class<T> modelClass) {
        Provider<? extends ViewModel> creator = creators.get(modelClass);
        if (creator == null) {
            for (Map.Entry<Class<? extends ViewModel>, Provider<ViewModel>> entry : creators.entrySet()) {
                if (modelClass.isAssignableFrom(entry.getKey())) {
                    creator = entry.getValue();
                    break;
                }
            }
        }
        if (creator == null) {
            throw new IllegalArgumentException("ViewModel 未在 XxxViewModelModule 中绑定：" + modelClass.getName());
        }
        return (T) creator.get();
    }
}
