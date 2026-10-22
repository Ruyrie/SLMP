package com.zhimu.slmp.health;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;
import com.zhimu.slmp.health.di.HealthInjector;
import com.zhimu.slmp.health.ui.immuneitem.ImmuneItemEditViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块08 免疫、用药与消毒组件入口：创建 HealthComponent。本组件没有一级页与对外服务；
 * 页面由今日首页、生产页、批次档案、待办与消息经 RouterPath（D01–D09）打开。
 */
public class HealthModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        HealthInjector.init(core);
    }

    /** 单独运行调试页（Mock 示例数据：东山猪场 1001，今天 2026-10-19） */
    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("D01 免疫计划 · ZM2610-031（batch_id=3001）", RouterPath.D01,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("D01 免疫计划 · 未带批次（先选批次）", RouterPath.D01));
        pages.add(new DebugPage("D02 添加免疫项目 · 无模板（batch_id=3205）", RouterPath.D02,
                id(RouterPath.EXTRA_BATCH_ID, 3205)));
        Bundle editItem = id(RouterPath.EXTRA_BATCH_ID, 3001);
        editItem.putLong(HealthExtras.EXTRA_PLAN_ID, 8117);
        editItem.putInt(HealthExtras.EXTRA_VERSION, 2);
        editItem.putString(HealthExtras.EXTRA_DOSE, "1");
        editItem.putString(HealthExtras.EXTRA_METHOD_CODE, "IM");
        editItem.putString(ImmuneItemEditViewModel.EXTRA_VACCINE_NAME, "猪瘟活疫苗 · 加强免疫");
        editItem.putString(ImmuneItemEditViewModel.EXTRA_PLAN_MODE, "BY_DAY_AGE");
        editItem.putString(ImmuneItemEditViewModel.EXTRA_PLAN_DATE, "2026-10-19");
        editItem.putInt(ImmuneItemEditViewModel.EXTRA_AGE_AT_PLAN, 96);
        editItem.putString(ImmuneItemEditViewModel.EXTRA_DOSE_UNIT, "头份 / 头");
        pages.add(new DebugPage("D02 修改免疫项目（plan_id=8117）", RouterPath.D02, editItem));
        pages.add(new DebugPage("D03 登记接种 · 猪瘟活疫苗加强免疫（plan_id=8117）", RouterPath.D03,
                id(HealthExtras.EXTRA_PLAN_ID, 8117)));
        Bundle backfill = id(HealthExtras.EXTRA_PLAN_ID, 8121);
        backfill.putBoolean(HealthExtras.EXTRA_BACKFILL, true);
        pages.add(new DebugPage("D03 补录漏免 · 伪狂犬病二免（plan_id=8121，员工为无权查看）", RouterPath.D03, backfill));
        pages.add(new DebugPage("D03 登记接种 · 未带计划（下拉选择）", RouterPath.D03));
        pages.add(new DebugPage("D03 登记接种 · 已被他人登记（plan_id=8132，40903）", RouterPath.D03,
                id(HealthExtras.EXTRA_PLAN_ID, 8132)));
        pages.add(new DebugPage("D03 接种记录查看（record_id=8790）", RouterPath.D03,
                id(HealthExtras.EXTRA_RECORD_ID, 8790)));
        pages.add(new DebugPage("D03 接种记录查看 · 当天可修改（record_id=8801）", RouterPath.D03,
                id(HealthExtras.EXTRA_RECORD_ID, 8801)));
        pages.add(new DebugPage("D04 登记用药 · ZM2610-045（batch_id=3003）", RouterPath.D04,
                id(RouterPath.EXTRA_BATCH_ID, 3003)));
        Bundle fromI07 = id(RouterPath.EXTRA_PEN_ID, 2001);
        fromI07.putLong(HealthExtras.EXTRA_REPORT_ID, 8101);
        fromI07.putLong(HealthExtras.EXTRA_DIAGNOSIS_ID, 8201);
        pages.add(new DebugPage("D04 登记用药 · 按栏舍并关联 AI 诊断（I07 转入）", RouterPath.D04, fromI07));
        pages.add(new DebugPage("D04 修改用药记录（record_id=5101）", RouterPath.D04,
                id(HealthExtras.EXTRA_RECORD_ID, 5101)));
        pages.add(new DebugPage("D05 用药档案 · 休药中（batch_id=3001）", RouterPath.D05,
                id(RouterPath.EXTRA_BATCH_ID, 3001)));
        pages.add(new DebugPage("D05 用药档案 · 当天记录可直接删除（batch_id=3002）", RouterPath.D05,
                id(RouterPath.EXTRA_BATCH_ID, 3002)));
        pages.add(new DebugPage("D05 用药档案 · 没有用药记录（batch_id=3003）", RouterPath.D05,
                id(RouterPath.EXTRA_BATCH_ID, 3003)));
        pages.add(new DebugPage("D05 用药档案 · 休药已结束（batch_id=3004）", RouterPath.D05,
                id(RouterPath.EXTRA_BATCH_ID, 3004)));
        pages.add(new DebugPage("D06 消毒计划（员工为无权查看）", RouterPath.D06));
        pages.add(new DebugPage("D07 新建消毒计划", RouterPath.D07));
        pages.add(new DebugPage("D07 修改消毒计划（plan_id=62）", RouterPath.D07, id(HealthExtras.EXTRA_PLAN_ID, 62)));
        pages.add(new DebugPage("D08 登记消毒 · 3 号育肥舍计划任务（task_id=70113）", RouterPath.D08,
                id(RouterPath.EXTRA_TASK_ID, 70113)));
        Bundle overdue = id(RouterPath.EXTRA_TASK_ID, 70112);
        overdue.putBoolean(HealthExtras.EXTRA_BACKFILL, true);
        pages.add(new DebugPage("D08 补录逾期任务 · 2 号保育舍（task_id=70112）", RouterPath.D08, overdue));
        Bundle temp = new Bundle();
        temp.putString(HealthExtras.EXTRA_RECORD_TYPE, HealthExtras.RECORD_EMPTY_PEN);
        pages.add(new DebugPage("D08 登记临时消毒（空栏消毒）", RouterPath.D08, temp));
        pages.add(new DebugPage("D08 登记消毒 · 未带任务", RouterPath.D08));
        pages.add(new DebugPage("D08 消毒记录查看 · 3 天内可修改（record_id=7101）", RouterPath.D08,
                id(HealthExtras.EXTRA_RECORD_ID, 7101)));
        pages.add(new DebugPage("D08 消毒记录查看 · 超过 3 天（record_id=7091）", RouterPath.D08,
                id(HealthExtras.EXTRA_RECORD_ID, 7091)));
        pages.add(new DebugPage("D09 消毒任务", RouterPath.D09));
        return pages;
    }

    private static Bundle id(String key, long value) {
        Bundle b = new Bundle();
        b.putLong(key, value);
        return b;
    }
}
