package com.zhimu.slmp.finance;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.common.service.TabRegistry;
import com.zhimu.slmp.finance.di.FinanceInjector;
import com.zhimu.slmp.finance.model.FinanceConst;
import com.zhimu.slmp.finance.ui.home.FinanceHomeFragment;
import com.zhimu.slmp.finance.util.ReportFileHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 模块10 收支、出栏、行情与报表组件入口：创建 FinanceComponent，注册底部导航“经营”（TabRegistry.BUSINESS，所有者与场长）；
 * 退出登录时删除本机导出的经营报表（技术准备文档 2.7 clearLocalData）。
 */
public class FinanceModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        FinanceInjector.init(core);
        TabRegistry.register(TabRegistry.BUSINESS, FinanceHomeFragment::new);
        core.sessionManager().getLoginState().observeForever(loggedIn -> {
            if (Boolean.FALSE.equals(loggedIn)) {
                Completable.fromAction(() -> new ReportFileHelper(app).clearAll())
                        .subscribeOn(Schedulers.io()).onErrorComplete().subscribe();
            }
        });
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("E01 经营页（以所有者登录）", RouterPath.E01));
        pages.add(new DebugPage("E02 收支记账", RouterPath.E02));
        pages.add(new DebugPage("E03 记一笔 · 支出", RouterPath.E03));
        pages.add(new DebugPage("E03 记一笔 · 收入", RouterPath.E03, str(FinanceExtras.RECORD_TYPE, FinanceConst.INCOME)));
        pages.add(new DebugPage("E03 修改手工记录（record_id=8203）", RouterPath.E03, id(FinanceExtras.RECORD_ID, 8203)));
        pages.add(new DebugPage("E04 收支流水", RouterPath.E04));
        pages.add(new DebugPage("E05 支出分类", RouterPath.E05));
        pages.add(new DebugPage("E06 登记出栏单", RouterPath.E06));
        pages.add(new DebugPage("E06 登记出栏单 · 休药中 ZM2610-031（batch_id=3001）", RouterPath.E06,
                id(FinanceExtras.BATCH_ID, 3001)));
        pages.add(new DebugPage("E07 出栏单详情 · 部分收款 ZM2609-012（9102）", RouterPath.E07,
                id(FinanceExtras.SALE_ORDER_ID, 9102)));
        pages.add(new DebugPage("E07 出栏单详情 · 未收款 ZM2610-019（9103）", RouterPath.E07,
                id(FinanceExtras.SALE_ORDER_ID, 9103)));
        pages.add(new DebugPage("E08 出栏单修改申请（9102）", RouterPath.E08, id(FinanceExtras.SALE_ORDER_ID, 9102)));
        pages.add(new DebugPage("E09 待收款", RouterPath.E09));
        pages.add(new DebugPage("E10 批次核算 · ZM2609-012 已锁定（batch_id=3009）", RouterPath.E10,
                id(FinanceExtras.BATCH_ID, 3009)));
        pages.add(new DebugPage("E10 批次核算 · ZM2610-031 在养（batch_id=3001）", RouterPath.E10,
                id(FinanceExtras.BATCH_ID, 3001)));
        pages.add(new DebugPage("E11 市场行情", RouterPath.E11));
        pages.add(new DebugPage("E12 AI 出栏建议", RouterPath.E12));
        pages.add(new DebugPage("E12 AI 出栏建议 · 可出栏 ZM2610-019（batch_id=3002）", RouterPath.E12,
                id(FinanceExtras.BATCH_ID, 3002)));
        pages.add(new DebugPage("E13 经营报表", RouterPath.E13));
        pages.add(new DebugPage("E13 经营报表 · 注销前导出（A09）", RouterPath.E13,
                str(RouterPath.EXTRA_MODE, RouterPath.MODE_OWNED_FARMS)));
        pages.add(new DebugPage("E14 出栏单列表", RouterPath.E14));
        return pages;
    }

    private static Bundle str(String key, String value) {
        Bundle b = new Bundle();
        b.putString(key, value);
        return b;
    }

    private static Bundle id(String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
