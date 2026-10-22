package com.zhimu.slmp.breed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.time.LocalDate;
import java.util.Map;

/**
 * “登记妊检”底部面板（PregCheckSheet）的宿主：B03 MatingRegisterViewModel 与 B06 AnimalArchiveViewModel 实现。
 * 面板复用宿主 ViewModel，字段错误键为 check_date、result。
 */
public interface PregCheckHost {

    /** 面板说明：“0155 · 长大二元 · 第 3 胎 · 9月24日配种，已 25 天” */
    @NonNull
    LiveData<String> getCheckSubtitle();

    /** 妊检日期（双向，yyyy-MM-dd） */
    @NonNull
    MutableLiveData<String> getCheckDate();

    /** 妊检结果（双向，POSITIVE / NEGATIVE / null） */
    @NonNull
    MutableLiveData<String> getCheckResult();

    /** 阳性说明：“转为妊娠，预产期 2027年1月16日 周六” */
    @NonNull
    LiveData<String> getPositiveHint();

    @NonNull
    LiveData<Map<String, String>> getCheckErrors();

    @NonNull
    LiveData<Boolean> getCheckSubmitting();

    /** 修改妊检记录（标题“修改妊检”、按钮“保存修改”） */
    @NonNull
    LiveData<Boolean> getCheckEditMode();

    /** 日期选择器下限：首次配种日期 */
    @Nullable
    LocalDate checkMinDate();

    void onCheckDatePicked(@Nullable String date);

    void onCheckResultPicked(@NonNull String result);

    void submitCheck();

    /** 妊检成功：关闭面板（值为提示文案，由宿主页面显示） */
    @NonNull
    LiveData<String> getDismissCheckSheet();

    /** 宿主 Activity 实现，供面板取得宿主 ViewModel */
    interface Provider {
        @NonNull
        PregCheckHost pregCheckHost();
    }
}
