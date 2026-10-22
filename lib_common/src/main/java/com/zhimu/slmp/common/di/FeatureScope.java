package com.zhimu.slmp.common.di;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Scope;

/**
 * 业务组件 Component 的作用域：组件内的 Repository、Api 为 @FeatureScope 单例（组件实例由 XxxInjector 持有，与进程同生命周期）。
 */
@Scope
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureScope {
}
