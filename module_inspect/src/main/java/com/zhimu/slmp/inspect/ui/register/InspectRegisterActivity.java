package com.zhimu.slmp.inspect.ui.register;

import androidx.annotation.NonNull;

import com.zhimu.slmp.inspect.ui.form.InspectFormActivity;

/**
 * I02 登记巡检（含修改当天记录、只读查看）：布局与交互见 {@link InspectFormActivity}。
 * 入口：I01 栏舍行或“登记巡检”、T02 快捷登记“巡检”、T04 待办“去登记”、I04“查看详情”“修改巡检”。
 */
public class InspectRegisterActivity extends InspectFormActivity<InspectRegisterViewModel> {

    @NonNull
    @Override
    protected Class<InspectRegisterViewModel> viewModelClass() {
        return InspectRegisterViewModel.class;
    }
}
