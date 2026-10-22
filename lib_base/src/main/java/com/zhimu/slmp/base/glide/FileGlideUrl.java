package com.zhimu.slmp.base.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.model.GlideUrl;

/**
 * 以 file_id 为缓存键的图片地址：服务端返回的 url 是临时地址，每次可能不同，按 file_id 缓存避免重复下载。
 */
public class FileGlideUrl extends GlideUrl {

    @NonNull
    private final String cacheKey;

    public FileGlideUrl(@NonNull String url, @NonNull String cacheKey) {
        super(url);
        this.cacheKey = cacheKey;
    }

    @NonNull
    @Override
    public String getCacheKey() {
        return cacheKey;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileGlideUrl && cacheKey.equals(((FileGlideUrl) o).cacheKey);
    }

    @Override
    public int hashCode() {
        return cacheKey.hashCode();
    }
}
