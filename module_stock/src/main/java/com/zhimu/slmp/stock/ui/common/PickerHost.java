package com.zhimu.slmp.stock.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

/**
 * 选择面板的宿主（Activity 实现）：面板不建 ViewModel，数据取自宿主页面的 ViewModel；搜索词交给宿主（300 ms 防抖后）。
 */
public interface PickerHost {

    /** 指定面板的数据 */
    @Nullable
    LiveData<PickerData> pickerData(@NonNull String pickerKey);

    /** 搜索框内容变化（已防抖 300 ms） */
    void onPickerSearch(@NonNull String pickerKey, @NonNull String keyword);
}
