package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.common.model.vo.FarmContextVO;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * 当前养殖场服务（模块02 实现）。
 */
public interface IFarmContextService {

    /**
     * 打开 F01 切换养殖场底部面板。
     *
     * @param allowAllFarms 是否显示“全部养殖场”（T03、T06、E02、E13）
     * @param inAllFarms    当前是否处于“全部养殖场”视角
     */
    void showSwitcher(@NonNull FragmentActivity host, boolean allowAllFarms, boolean inAllFarms);

    /** 切换到指定养殖场（API-F03），成功后已写入 FarmContext */
    @NonNull
    Single<FarmContextVO> switchTo(long farmId);

    /** 重新获取当前养殖场上下文（API-F02，启动时与角色变化后），没有可用养殖场时按模块02 2.6.1 节处理 */
    @NonNull
    Completable refreshContext();

    @NonNull
    LiveData<FarmContextVO> context();
}
