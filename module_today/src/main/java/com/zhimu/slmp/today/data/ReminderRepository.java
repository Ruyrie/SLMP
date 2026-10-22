package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.today.model.request.ReminderSettingPatchRequest;
import com.zhimu.slmp.today.model.vo.ReminderSettingVO;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 提醒设置（API-T10、T11），对账号生效，不需要养殖场上下文。
 */
@FeatureScope
public class ReminderRepository {

    private final TodayApi api;

    @Inject
    public ReminderRepository(@NonNull TodayApi api) {
        this.api = api;
    }

    /** API-T10 */
    @NonNull
    public Single<ReminderSettingVO> get() {
        return api.getReminderSetting().compose(RxResult.unwrap());
    }

    /** API-T11：返回完整设置 */
    @NonNull
    public Single<ReminderSettingVO> patch(@NonNull ReminderSettingPatchRequest body) {
        return api.patchReminderSetting(body).compose(RxResult.unwrap());
    }
}
