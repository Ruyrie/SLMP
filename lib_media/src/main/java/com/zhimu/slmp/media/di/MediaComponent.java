package com.zhimu.slmp.media.di;

import com.zhimu.slmp.base.mvvm.ViewModelFactory;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.media.data.MediaRepository;
import com.zhimu.slmp.media.upload.UploadManager;

import dagger.Component;

/**
 * 多媒体组件的 Dagger 组件：依赖 CoreComponent，提供相机页、全屏预览的 ViewModel 工厂与上传工厂。
 * 业务组件的 ViewModel 可直接注入 {@link UploadManager.Factory}（依赖都来自 CoreComponent），不必经过本组件。
 */
@FeatureScope
@Component(dependencies = CoreComponent.class, modules = MediaViewModelModule.class)
public interface MediaComponent {

    ViewModelFactory viewModelFactory();

    UploadManager.Factory uploadFactory();

    MediaRepository mediaRepository();

    @Component.Factory
    interface Factory {
        MediaComponent create(CoreComponent core);
    }
}
