package com.zhimu.slmp.today.ui.setting;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.service.IPushService;
import com.zhimu.slmp.today.data.ReminderRepository;
import com.zhimu.slmp.today.model.request.ReminderSettingPatchRequest;
import com.zhimu.slmp.today.model.vo.ReminderSettingVO;
import com.zhimu.slmp.today.push.NotificationChannels;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * T05 提醒设置（ReminderSettingActivity、TimePickerSheet），技术文档 2.4.4、2.6.5：
 * 开关或时间变化后立即显示，再经 PublishSubject + concatMap 逐个调用 API-T11（快速连点时按顺序到达）；
 * 成功用返回的完整设置覆盖，失败回滚到最近一次服务端值并提示“设置没有保存，请检查网络后重试”。
 */
public class ReminderSettingViewModel extends BaseViewModel {

    private static final String TAG = "ReminderSettingVM";
    static final String MSG_SAVE_FAILED = "设置没有保存，请检查网络后重试";

    private final ReminderRepository repository;
    private final IPushService pushService;

    private final MutableLiveData<Boolean> todoRemindEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> digestEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> quietEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<String> digestTime = new MutableLiveData<>("07:00");
    private final MutableLiveData<String> quietStart = new MutableLiveData<>("22:00");
    private final MutableLiveData<String> quietEnd = new MutableLiveData<>("07:00");
    private final MutableLiveData<Boolean> quietEndNextDay = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> notifyBlocked = new MutableLiveData<>(false);

    private final SingleLiveEvent<String> openTimePicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openNotificationSettings = new SingleLiveEvent<>();

    private final PublishSubject<ReminderSettingPatchRequest> saves = PublishSubject.create();
    /** 最近一次服务端返回的设置（失败时据此回滚） */
    @Nullable
    private ReminderSettingVO server;
    private int pending;
    @Nullable
    private Boolean lastSystemEnabled;
    @Nullable
    private Boolean lastChannelOn;

    @Inject
    public ReminderSettingViewModel(@NonNull ReminderRepository repository, @NonNull IPushService pushService) {
        this.repository = repository;
        this.pushService = pushService;
        disposables.add(saves
                .concatMapSingle(req -> repository.patch(req)
                        .subscribeOn(Schedulers.io())
                        .map(SaveResult::ok)
                        .onErrorReturn(SaveResult::fail))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSaved, t -> Log.w(TAG, "保存队列异常", t)));
        load();
    }

    // ===================== 对外 LiveData =====================

    public LiveData<Boolean> getTodoRemindEnabled() {
        return todoRemindEnabled;
    }

    public LiveData<Boolean> getDigestEnabled() {
        return digestEnabled;
    }

    public LiveData<Boolean> getQuietEnabled() {
        return quietEnabled;
    }

    public LiveData<String> getDigestTime() {
        return digestTime;
    }

    public LiveData<String> getQuietStart() {
        return quietStart;
    }

    public LiveData<String> getQuietEnd() {
        return quietEnd;
    }

    public LiveData<Boolean> getQuietEndNextDay() {
        return quietEndNextDay;
    }

    public LiveData<Boolean> getNotifyBlocked() {
        return notifyBlocked;
    }

    public SingleLiveEvent<String> getOpenTimePicker() {
        return openTimePicker;
    }

    public SingleLiveEvent<String> getOpenNotificationSettings() {
        return openNotificationSettings;
    }

    // ===================== 加载 =====================

    public void load() {
        load(repository.get(), this::apply);
    }

    @Override
    public void retry() {
        load();
    }

    @VisibleForTesting
    void apply(@NonNull ReminderSettingVO vo) {
        server = vo;
        todoRemindEnabled.setValue(vo.isTodoRemindEnabled());
        digestEnabled.setValue(vo.isDigestEnabled());
        quietEnabled.setValue(vo.isQuietEnabled());
        digestTime.setValue(vo.getDigestTime());
        quietStart.setValue(vo.getQuietStart());
        quietEnd.setValue(vo.getQuietEnd());
        quietEndNextDay.setValue(vo.isQuietEndNextDay());
    }

    // ===================== 修改 =====================

    /** 开关：立即显示并保存 */
    public void toggle(@NonNull String field, boolean value) {
        MutableLiveData<Boolean> target = switchOf(field);
        if (target == null || Boolean.valueOf(value).equals(target.getValue())) {
            return;
        }
        target.setValue(value);
        enqueue(ReminderSettingPatchRequest.ofSwitch(field, value));
    }

    /** 点时间行：打开 TimePickerSheet */
    public void onTimeClick(@NonNull String field) {
        openTimePicker.setValue(field);
    }

    /** 时间选择面板“确定”：hhmm 为 HH:mm，分钟为 5 的倍数 */
    public void onTimePicked(@NonNull String field, @NonNull String hhmm) {
        MutableLiveData<String> target = timeOf(field);
        if (target == null || hhmm.equals(target.getValue())) {
            return;
        }
        target.setValue(hhmm);
        if (!ReminderSettingPatchRequest.FIELD_DIGEST_TIME.equals(field)) {
            quietEndNextDay.setValue(isNextDay(quietStart.getValue(), quietEnd.getValue()));
        }
        enqueue(ReminderSettingPatchRequest.ofTime(field, hhmm));
    }

    /** 结束时间早于（不晚于）开始时间时为次日 */
    static boolean isNextDay(@Nullable String start, @Nullable String end) {
        return start != null && end != null && end.compareTo(start) <= 0;
    }

    /** 时间面板读取的当前值 */
    @NonNull
    public String timeValue(@NonNull String field) {
        MutableLiveData<String> t = timeOf(field);
        return t == null || t.getValue() == null ? "07:00" : t.getValue();
    }

    /** 免打扰另一端的时间（开始与结束不能相同）；汇总时间返回 null */
    @Nullable
    public String otherEnd(@NonNull String field) {
        if (ReminderSettingPatchRequest.FIELD_QUIET_START.equals(field)) {
            return quietEnd.getValue();
        }
        if (ReminderSettingPatchRequest.FIELD_QUIET_END.equals(field)) {
            return quietStart.getValue();
        }
        return null;
    }

    private void enqueue(@NonNull ReminderSettingPatchRequest req) {
        pending++;
        saves.onNext(req);
    }

    private void onSaved(@NonNull SaveResult r) {
        pending = Math.max(0, pending - 1);
        if (r.vo != null) {
            server = r.vo;
            if (pending == 0) {
                apply(r.vo);
            }
            return;
        }
        if (server != null) {
            apply(server);
        }
        showMessage(MSG_SAVE_FAILED);
    }

    @VisibleForTesting
    int pendingCount() {
        return pending;
    }

    @Nullable
    private MutableLiveData<Boolean> switchOf(@NonNull String field) {
        switch (field) {
            case ReminderSettingPatchRequest.FIELD_TODO_REMIND:
                return todoRemindEnabled;
            case ReminderSettingPatchRequest.FIELD_DIGEST:
                return digestEnabled;
            case ReminderSettingPatchRequest.FIELD_QUIET:
                return quietEnabled;
            default:
                return null;
        }
    }

    @Nullable
    private MutableLiveData<String> timeOf(@NonNull String field) {
        switch (field) {
            case ReminderSettingPatchRequest.FIELD_DIGEST_TIME:
                return digestTime;
            case ReminderSettingPatchRequest.FIELD_QUIET_START:
                return quietStart;
            case ReminderSettingPatchRequest.FIELD_QUIET_END:
                return quietEnd;
            default:
                return null;
        }
    }

    // ===================== 系统通知权限 =====================

    /** 页面每次 onResume 检测系统通知总开关与 zm_todo 渠道；变化时重新上报设备（API-T20） */
    public void onPermissionChecked(boolean enabled, boolean todoChannelOn) {
        boolean changed = lastSystemEnabled != null
                && (lastSystemEnabled != enabled || !Boolean.valueOf(todoChannelOn).equals(lastChannelOn));
        lastSystemEnabled = enabled;
        lastChannelOn = todoChannelOn;
        notifyBlocked.setValue(!(enabled && todoChannelOn));
        if (changed) {
            pushService.syncDevice();
        }
    }

    /** “去开启”：总开关关闭时打开应用通知设置（空串），只关了待办渠道时打开 zm_todo 渠道设置 */
    public void onOpenSettingsClick() {
        boolean systemOn = Boolean.TRUE.equals(lastSystemEnabled);
        openNotificationSettings.setValue(systemOn ? NotificationChannels.TODO : "");
    }

    /** 保存结果 */
    private static final class SaveResult {
        @Nullable
        final ReminderSettingVO vo;

        private SaveResult(@Nullable ReminderSettingVO vo) {
            this.vo = vo;
        }

        static SaveResult ok(@NonNull ReminderSettingVO vo) {
            return new SaveResult(vo);
        }

        static SaveResult fail(@NonNull Throwable t) {
            return new SaveResult(null);
        }
    }
}
