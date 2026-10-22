package com.zhimu.slmp.media.debug;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.common.di.CoreInjector;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.media.MediaModuleEntry;
import com.zhimu.slmp.media.databinding.MediaDebugActivityDemoBinding;

import java.util.List;

/**
 * 照片网格演示页（只在 debug 包中）：可编辑网格（资质图，最多 9 张，带入 2 张已有文件）与只读网格（死淘照片）。
 */
public class MediaDemoActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaDebugActivityDemoBinding b = MediaDebugActivityDemoBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        MediaDemoViewModel vm = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                MediaDemoViewModel created = new MediaDemoViewModel();
                created.attachSavedState(SavedStateHandleSupport.createSavedStateHandle(extras));
                return (T) created;
            }
        }).get(MediaDemoViewModel.class);
        observeBase(vm);

        b.mediaDemoBack.setOnClickListener(v -> finish());
        b.mediaDemoEditable.bind(this, vm.getQualFiles());
        b.mediaDemoSubmit.setOnClickListener(v -> vm.submit());

        String base = MediaModuleEntry.MOCK_IMAGE_BASE;
        String json = "[{\"file_id\":9004,\"url\":\"" + base + "death_photo.jpg\",\"thumb_url\":\"" + base
                + "death_photo.jpg\",\"media_type\":\"IMAGE\",\"sort_no\":1,\"source_text\":\"死淘登记 · 3 号育肥舍\","
                + "\"shot_by\":\"李明\",\"shot_at\":\"2026-10-19 09:28:00\"},"
                + "{\"file_id\":9003,\"url\":\"" + base + "farm_env.jpg\",\"thumb_url\":\"" + base
                + "farm_env.jpg\",\"media_type\":\"IMAGE\",\"sort_no\":2,\"source_text\":\"死淘登记 · 3 号育肥舍\","
                + "\"shot_by\":\"李明\",\"shot_at\":\"2026-10-19 09:29:00\"}]";
        Gson gson = CoreInjector.get(this).gson();
        List<MediaItemVO> photos = gson.fromJson(json, new TypeToken<List<MediaItemVO>>() {
        }.getType());
        b.mediaDemoReadonly.setReadOnlyFiles(photos, "死淘登记 · 3 号育肥舍");
    }
}
