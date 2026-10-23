package com.zhimu.slmp.breed;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.zhimu.slmp.breed.di.BreedInjector;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.common.service.DebugPage;
import com.zhimu.slmp.common.service.IModuleEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模块05 繁育管理组件入口：创建 BreedComponent；本组件没有底部导航标签与对外服务。
 * debugPages 列出 B01–B07 与档案表单（Mock：东山猪场母猪舍 2006、产房 2007；西岭鸡场孵化 6001–6006）。
 */
public class BreedModuleEntry implements IModuleEntry {

    @Override
    public void onCreate(@NonNull Application app, @NonNull CoreComponent core) {
        BreedInjector.init(core);
    }

    @NonNull
    @Override
    public List<DebugPage> debugPages() {
        List<DebugPage> pages = new ArrayList<>();
        pages.add(new DebugPage("B01 种畜与待产（以所有者登录；员工为无权查看）", RouterPath.B01));
        pages.add(new DebugPage("B01 已淘汰和死亡的母猪", RouterPath.B01, str(RouterPath.EXTRA_MODE,
                BreedExtras.MODE_EXITED)));
        pages.add(new DebugPage("B02 待产与待出雏 · 待产（以员工登录为只读版）", RouterPath.B02));
        pages.add(new DebugPage("B02 待产与待出雏 · 待出雏（东山猪场显示跨场横幅）", RouterPath.B02,
                str(RouterPath.EXTRA_TAB, BreedExtras.TAB_HATCH)));
        pages.add(new DebugPage("B03 登记配种 / 妊检（下拉选择母猪）", RouterPath.B03));
        pages.add(new DebugPage("B03 登记配种 · 预选空怀 0161（animal_id=4161）", RouterPath.B03,
                id(RouterPath.EXTRA_ANIMAL_ID, 4161)));
        pages.add(new DebugPage("B03 同一情期再次配种 · 0155（animal_id=4155）", RouterPath.B03,
                id(RouterPath.EXTRA_ANIMAL_ID, 4155)));
        pages.add(new DebugPage("B04 登记产仔 · 0131 今天预产（animal_id=4131，活仔填 45 触发超容量）", RouterPath.B04,
                id(RouterPath.EXTRA_ANIMAL_ID, 4131)));
        pages.add(new DebugPage("B04 登记产仔（未带母猪，下拉选择妊娠母猪）", RouterPath.B04));
        pages.add(new DebugPage("B04 修改产仔记录 · 0118 未转批次（record_id=8118）", RouterPath.B04,
                id(RouterPath.EXTRA_RECORD_ID, 8118)));
        pages.add(new DebugPage("B05 登记断奶（下拉选择哺乳母猪）", RouterPath.B05));
        pages.add(new DebugPage("B05 登记断奶 · 预选 0109（animal_id=4109）", RouterPath.B05,
                id(RouterPath.EXTRA_ANIMAL_ID, 4109)));
        pages.add(new DebugPage("B06 种畜档案 · 0161 空怀（animal_id=4161）", RouterPath.B06,
                id(RouterPath.EXTRA_ANIMAL_ID, 4161)));
        pages.add(new DebugPage("B06 种畜档案 · 0155 已配种（头部登记妊检）", RouterPath.B06,
                id(RouterPath.EXTRA_ANIMAL_ID, 4155)));
        pages.add(new DebugPage("B06 种畜档案 · 0131 妊娠（头部登记产仔）", RouterPath.B06,
                id(RouterPath.EXTRA_ANIMAL_ID, 4131)));
        pages.add(new DebugPage("B06 种畜档案 · 0127 哺乳（产仔记录修改申请审核中）", RouterPath.B06,
                id(RouterPath.EXTRA_ANIMAL_ID, 4127)));
        pages.add(new DebugPage("B06 种畜档案 · 0102 已淘汰（只读）", RouterPath.B06,
                id(RouterPath.EXTRA_ANIMAL_ID, 4102)));
        pages.add(new DebugPage("档案表单 · 新增母猪（耳号填 0161 触发重复）", BreedExtras.ACTION_ANIMAL_FORM));
        pages.add(new DebugPage("档案表单 · 编辑 0161（animal_id=4161）", BreedExtras.ACTION_ANIMAL_FORM,
                id(RouterPath.EXTRA_ANIMAL_ID, 4161)));
        pages.add(new DebugPage("B07 禽类孵化 · 第 1 步登记入孵（新建）", RouterPath.B07));
        pages.add(new DebugPage("B07 禽类孵化 · 第 2 步照蛋（incubation_id=6003）", RouterPath.B07,
                id(RouterPath.EXTRA_INCUBATION_ID, 6003)));
        pages.add(new DebugPage("B07 禽类孵化 · 第 3 步出雏 1 号种鸡舍 · ZM2603-015（incubation_id=6001，"
                + "选 2 号鸡舍触发超容量）", RouterPath.B07, id(RouterPath.EXTRA_INCUBATION_ID, 6001)));
        pages.add(new DebugPage("B07 禽类孵化 · 已出雏结果（incubation_id=6004）", RouterPath.B07,
                id(RouterPath.EXTRA_INCUBATION_ID, 6004)));
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
