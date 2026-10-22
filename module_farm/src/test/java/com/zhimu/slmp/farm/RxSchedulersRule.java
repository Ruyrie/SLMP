package com.zhimu.slmp.farm;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 单元测试把 RxJava 的 io、computation 与主线程调度器替换为同步执行（trampoline）。
 */
public class RxSchedulersRule extends TestWatcher {

    @Override
    protected void starting(Description description) {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxAndroidPlugins.setMainThreadSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setComputationSchedulerHandler(s -> Schedulers.trampoline());
        RxJavaPlugins.setNewThreadSchedulerHandler(s -> Schedulers.trampoline());
    }

    @Override
    protected void finished(Description description) {
        RxJavaPlugins.reset();
        RxAndroidPlugins.reset();
    }
}
