package com.zhimu.slmp.base.mvvm;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 只消费一次的 LiveData：用于提示、跳转、打开弹窗，屏幕旋转后不会重复触发。
 * 同一时刻只应有一个活跃观察者。
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

    private final AtomicBoolean pending = new AtomicBoolean(false);

    @MainThread
    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, value -> {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(value);
            }
        });
    }

    @MainThread
    @Override
    public void setValue(@Nullable T value) {
        pending.set(true);
        super.setValue(value);
    }

    /** 无参数事件 */
    @MainThread
    public void call() {
        setValue(null);
    }
}
