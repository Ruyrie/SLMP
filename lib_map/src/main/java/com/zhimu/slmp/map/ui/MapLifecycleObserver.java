package com.zhimu.slmp.map.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.amap.api.maps.TextureMapView;

/**
 * 地图生命周期封装（03 号文档 9.1 节）：观察页面生命周期，转发 TextureMapView 的创建、恢复、暂停、销毁；
 * 状态保存由页面在 onSaveInstanceState 中调用 {@link #onSaveInstanceState(Bundle)}。销毁时执行 onRelease（例如释放定位客户端）。
 * <pre>
 * observer = new MapLifecycleObserver(mapView, savedInstanceState);
 * getLifecycle().addObserver(observer);
 * </pre>
 */
public class MapLifecycleObserver implements DefaultLifecycleObserver {

    @NonNull
    private final TextureMapView mapView;
    @Nullable
    private final Bundle savedState;
    @Nullable
    private Runnable onRelease;
    private boolean created;
    private boolean destroyed;

    public MapLifecycleObserver(@NonNull TextureMapView mapView, @Nullable Bundle savedState) {
        this.mapView = mapView;
        this.savedState = savedState;
    }

    /** 页面销毁时额外执行的释放动作 */
    public void setOnRelease(@Nullable Runnable onRelease) {
        this.onRelease = onRelease;
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        if (!created) {
            created = true;
            mapView.onCreate(savedState);
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        mapView.onResume();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        mapView.onPause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        owner.getLifecycle().removeObserver(this);
        if (destroyed) {
            return;
        }
        destroyed = true;
        if (onRelease != null) {
            onRelease.run();
        }
        mapView.onDestroy();
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (created && !destroyed) {
            mapView.onSaveInstanceState(outState);
        }
    }

    public void onLowMemory() {
        if (created && !destroyed) {
            mapView.onLowMemory();
        }
    }
}
