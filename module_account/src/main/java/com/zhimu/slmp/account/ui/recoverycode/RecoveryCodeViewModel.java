package com.zhimu.slmp.account.ui.recoverycode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.util.RecoveryCodeHolder;
import com.zhimu.slmp.account.util.RecoveryCodeWatcher;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * A04 保存恢复码（2.4.4 节）：恢复码来自 RecoveryCodeHolder（A03、A13 的 recovery_code），为空时调 API-A12 重新生成；
 * 勾选“我已保存恢复码”后调 API-A14 确认。明文只在内存，onCleared 时置 null。
 */
public class RecoveryCodeViewModel extends BaseViewModel {

    public static final String SCENE_REGISTER = "REGISTER";
    public static final String SCENE_RECOVERY = "RECOVERY";
    public static final String SCENE_REGENERATE = "REGENERATE";

    public static final String DESC_REGISTER = "注册成功。手机号不能使用（停机、丢失、换号）时，需要用这个恢复码找回账号。";
    public static final String DESC_RECOVERY = "密码已修改。旧恢复码已作废，下面是新的恢复码。";
    public static final String DESC_REGENERATE = "已生成新的恢复码，旧恢复码已作废。手机号不能使用时，需要用这个恢复码找回账号。";
    public static final String DESC_REGENERATED = "上次的恢复码没有确认保存，已重新生成。请保存下面的新恢复码。";
    public static final String MSG_CODE_UPDATED = "恢复码已更新，请保存最新的恢复码";
    public static final String TEXT_ENTER = "进入智牧通";
    public static final String TEXT_DONE = "完成";

    private final AccountRepository repository;
    private final RecoveryCodeHolder codeHolder;

    private final MutableLiveData<RecoveryCodeVO> recoveryCode = new MutableLiveData<>(null);
    private final LiveData<String> codeGroups;
    private final LiveData<String> descText;
    private final LiveData<String> primaryText;
    public final MutableLiveData<Boolean> savedChecked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();

    private final SingleLiveEvent<String> copyText = new SingleLiveEvent<>();
    private final SingleLiveEvent<RecoveryCodeVO> saveImage = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> done = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> nextStep = new SingleLiveEvent<>();

    @Inject
    public RecoveryCodeViewModel(@NonNull AccountRepository repository, @NonNull RecoveryCodeHolder codeHolder) {
        this.repository = repository;
        this.codeHolder = codeHolder;
        codeGroups = Transformations.map(recoveryCode, c -> c == null ? null : RecoveryCodeWatcher.format(c.getCode()));
        descText = Transformations.map(recoveryCode, RecoveryCodeViewModel::descOf);
        primaryText = Transformations.map(recoveryCode,
                c -> c != null && SCENE_REGENERATE.equals(c.getScene()) ? TEXT_DONE : TEXT_ENTER);
        canSubmit.setValue(false);
        canSubmit.addSource(savedChecked, v -> updateCanSubmit());
        canSubmit.addSource(submitting, v -> updateCanSubmit());
        canSubmit.addSource(recoveryCode, v -> updateCanSubmit());
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        start();
    }

    @Nullable
    static String descOf(@Nullable RecoveryCodeVO c) {
        if (c == null) {
            return null;
        }
        if (c.isRegenerated()) {
            return DESC_REGENERATED;
        }
        if (SCENE_RECOVERY.equals(c.getScene())) {
            return DESC_RECOVERY;
        }
        if (SCENE_REGENERATE.equals(c.getScene())) {
            return DESC_REGENERATE;
        }
        return DESC_REGISTER;
    }

    private void updateCanSubmit() {
        Boolean v = Boolean.TRUE.equals(savedChecked.getValue()) && recoveryCode.getValue() != null
                && !Boolean.TRUE.equals(submitting.getValue());
        if (!v.equals(canSubmit.getValue())) {
            canSubmit.setValue(v);
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<RecoveryCodeVO> getRecoveryCode() {
        return recoveryCode;
    }

    public LiveData<String> getCodeGroups() {
        return codeGroups;
    }

    public LiveData<String> getDescText() {
        return descText;
    }

    public LiveData<String> getPrimaryText() {
        return primaryText;
    }

    public LiveData<Boolean> getSubmitting() {
        return submitting;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public SingleLiveEvent<String> getCopyText() {
        return copyText;
    }

    public SingleLiveEvent<RecoveryCodeVO> getSaveImage() {
        return saveImage;
    }

    public SingleLiveEvent<String> getDone() {
        return done;
    }

    public SingleLiveEvent<String> getNextStep() {
        return nextStep;
    }

    // ===== 方法 =====

    /** 取 RecoveryCodeHolder 中的码；为空（进程被回收、冷启动进入）时调 API-A12 */
    public void start() {
        RecoveryCodeVO held = codeHolder.take();
        if (held != null && held.getCode() != null) {
            recoveryCode.setValue(held);
            return;
        }
        regenerate();
    }

    private void regenerate() {
        resetContentLoaded();
        load(repository.regeneratePendingCode(), recoveryCode::setValue);
    }

    @Override
    public void retry() {
        regenerate();
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.FORCED_STEP_REQUIRED) {
            nextStep.setValue(e.dataString("next_step"));
            return;
        }
        super.applyErrorState(e);
    }

    public void copyCode() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c != null) {
            copyText.setValue(RecoveryCodeWatcher.format(c.getCode()));
        }
    }

    public void saveImage() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c != null) {
            saveImage.setValue(c);
        }
    }

    /** API-A14；注册与找回场景确认后按 last_farm_id 恢复当前养殖场再进入首页 */
    public void confirm() {
        RecoveryCodeVO c = recoveryCode.getValue();
        if (c == null || Boolean.TRUE.equals(submitting.getValue()) || !Boolean.TRUE.equals(savedChecked.getValue())) {
            return;
        }
        submitting.setValue(true);
        String scene = c.getScene() == null ? SCENE_REGISTER : c.getScene();
        Single<String> source = repository.confirmRecoveryCode(c.getRecoveryCodeId())
                .flatMap(vo -> {
                    if (SCENE_REGENERATE.equals(scene)) {
                        return Single.just(scene);
                    }
                    return repository.getSession()
                            .flatMapCompletable(s -> repository.restoreFarm(s.getLastFarmId()))
                            .onErrorComplete()
                            .toSingleDefault(scene);
                });
        launch(source, s -> {
            submitting.setValue(false);
            recoveryCode.setValue(null);
            done.setValue(s);
        }, e -> {
            submitting.setValue(false);
            onConfirmError(e);
        });
    }

    private void onConfirmError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.STATE_CHANGED || code == ErrorCodes.NOT_FOUND) {
            showMessage(MSG_CODE_UPDATED);
            reloadAfterConflict();
            return;
        }
        if (code == ErrorCodes.FORCED_STEP_REQUIRED) {
            nextStep.setValue(e.dataString("next_step"));
            return;
        }
        handleError(e);
    }

    /** 40903 / 40401：API-A08 确认仍为 SAVE_RECOVERY_CODE 时调 API-A12 重新取码，勾选清空 */
    public void reloadAfterConflict() {
        savedChecked.setValue(false);
        launch(repository.getSession(), s -> {
            if (AuthTokenVO.NEXT_SAVE_RECOVERY_CODE.equals(s.getNextStep())) {
                regenerate();
            } else {
                nextStep.setValue(s.getNextStep());
            }
        });
    }

    @Override
    protected void onCleared() {
        recoveryCode.setValue(null);
        super.onCleared();
    }
}
