package com.zhimu.slmp.base.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.R;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * 防连点：500 毫秒内只响应第一次点击（RxJava throttleFirst）。
 * 布局中可用 CommonBindingAdapters 的 {@code app:onSafeClick}。
 */
public final class ClickThrottle {

    public static final long WINDOW_MS = 500;

    private ClickThrottle() {
    }

    /** 视图点击流（已防连点），订阅期间占用视图的 OnClickListener */
    @NonNull
    public static Observable<View> clicks(@NonNull View view) {
        return Observable.<View>create(emitter -> {
                    view.setOnClickListener(emitter::onNext);
                    emitter.setCancellable(() -> view.setOnClickListener(null));
                })
                .throttleFirst(WINDOW_MS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread());
    }

    /** 设置防连点的点击监听；listener 为 null 时清除 */
    public static void setOnClickListener(@NonNull View view, @Nullable View.OnClickListener listener) {
        Object old = view.getTag(TAG_KEY);
        if (old instanceof Disposable) {
            ((Disposable) old).dispose();
        }
        if (listener == null) {
            view.setOnClickListener(null);
            return;
        }
        // 订阅只被视图自身引用，视图回收时一并回收，无需在 detach 时取消（RecyclerView 复用的视图仍可点击）
        Disposable d = clicks(view).subscribe(listener::onClick);
        view.setTag(TAG_KEY, d);
    }

    private static final int TAG_KEY = R.id.base_core_click_throttle;
}
