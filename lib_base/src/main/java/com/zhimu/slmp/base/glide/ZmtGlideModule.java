package com.zhimu.slmp.base.glide;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.zhimu.slmp.base.BuildConfig;

/**
 * 全应用唯一的 Glide 模块：网络图片磁盘缓存上限 250MB（cacheDir/image_cache，“我的”页可清理）。
 * 其他组件不要再定义 AppGlideModule，需要扩展时用 LibraryGlideModule。
 */
@GlideModule
public final class ZmtGlideModule extends AppGlideModule {

    public static final String DISK_CACHE_DIR = "image_cache";
    public static final long DISK_CACHE_SIZE = 250L * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_DIR, DISK_CACHE_SIZE));
        builder.setLogLevel(BuildConfig.DEBUG ? Log.WARN : Log.ERROR);
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
