package com.zhimu.slmp.today;

import androidx.annotation.NonNull;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 单元测试：io 与主线程调度器替换为同步执行（computation 保持原样，timeout 的计时不阻塞测试线程）。
 */
public class RxTestRule extends TestWatcher {

    @Override
    protected void starting(@NonNull Description description) {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setNewThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(s -> Schedulers.trampoline());
    }

    @Override
    protected void finished(@NonNull Description description) {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
    }
}
