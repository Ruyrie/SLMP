package com.zhimu.slmp.media.di;

import androidx.lifecycle.ViewModel;

import com.zhimu.slmp.base.mvvm.ViewModelFactoryModule;
import com.zhimu.slmp.base.mvvm.ViewModelKey;
import com.zhimu.slmp.media.ui.camera.CameraViewModel;
import com.zhimu.slmp.media.ui.viewer.MediaViewerViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

/**
 * 多媒体组件的 ViewModel 多绑定。
 */
@Module(includes = ViewModelFactoryModule.class)
public abstract class MediaViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(CameraViewModel.class)
    abstract ViewModel bindCamera(CameraViewModel vm);

    @Binds
    @IntoMap
    @ViewModelKey(MediaViewerViewModel.class)
    abstract ViewModel bindViewer(MediaViewerViewModel vm);
}
