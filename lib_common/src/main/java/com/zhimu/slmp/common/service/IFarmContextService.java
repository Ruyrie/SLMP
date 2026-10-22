package com.zhimu.slmp.common.service;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.zhimu.slmp.common.model.vo.FarmContextVO;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import com.zhimu.slmp.common.model.vo.FarmBriefVO;

/**
 * 当前养殖场服务（模块02 实现）。
 */
public interface IFarmContextService {

    /**
     * F01 切换面板的结果（allowAllFarms 为真时，选“全部养殖场”或在全部视角下选某个养殖场才回传）：
     * 宿主在自己的 FragmentManager 上监听 {@link #SWITCHER_REQUEST_KEY}。
     */
    String SWITCHER_REQUEST_KEY = "farm_switcher_result";
    /** 结果：是否选了“全部养殖场”（boolean） */
    String SWITCHER_RESULT_ALL_FARMS = "all_farms";
    /** 结果：选中的养殖场（long） */
    String SWITCHER_RESULT_FARM_ID = "farm_id";

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

    /**
     * 场长、员工主动退出养殖场（API-F27）；退出的是当前养殖场时按模块02 2.6.1 节重定当前养殖场（切到下一个，
     * 都没有时清空 FarmContext）。所有者不能退出（服务端返回 40303），需先转让所有权。
     */
    @NonNull
    Completable quitFarm(long farmId);

    /** 我所在的全部养殖场（API-F01 第 1 页，最多 100 个），用于 T06 等页面的养殖场筛选 */
    @NonNull
    Single<List<FarmBriefVO>> listMyFarms();
}
