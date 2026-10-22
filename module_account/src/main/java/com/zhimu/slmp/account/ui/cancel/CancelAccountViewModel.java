package com.zhimu.slmp.account.ui.cancel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.request.AccountCancelRequest;
import com.zhimu.slmp.account.model.request.AccountSmsCodeRequest;
import com.zhimu.slmp.account.model.vo.CancelExitFarmVO;
import com.zhimu.slmp.account.model.vo.CancelOwnedFarmVO;
import com.zhimu.slmp.account.model.vo.CancellationPreviewVO;
import com.zhimu.slmp.account.ui.base.AccountFormViewModel;
import com.zhimu.slmp.account.util.AccountForm;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.form.FormValidator;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * A09 注销账号（2.4.11 节）：注销预览（API-A19）；处理名下养殖场（转让 F10、停用 F05、导出 E13）；
 * 满足条件后勾选并短信验证（API-A02 CANCEL_ACCOUNT），确认后注销（API-A20，幂等键，带 checklist_version）。
 */
public class CancelAccountViewModel extends AccountFormViewModel {

    public static final String FIELD_SMS_CODE = "sms_code";
    public static final String FIELD_CONFIRMED = "confirmed";
    public static final String ACTION_TRANSFER = "TRANSFER";
    public static final String ACTION_DISABLE = "DISABLE";
    public static final String MSG_NO_TRANSFERABLE = "这些养殖场还没有场长，不能转让。可以先在成员管理中邀请场长。";
    public static final String MSG_CANCELLED = "账号已注销";

    private final AccountRepository repository;
    private final FarmContext farmContext;

    private final MutableLiveData<CancellationPreviewVO> preview = new MutableLiveData<>(null);
    private final LiveData<List<CancelOwnedFarmVO>> ownedFarms;
    private final LiveData<List<CancelExitFarmVO>> exitFarms;
    private final LiveData<String> recoveryBanner;
    private final LiveData<String> farmBanner;
    private final LiveData<Boolean> showFarmActions;
    private final LiveData<Boolean> showExport;
    private final LiveData<Boolean> showConfirmArea;
    public final MutableLiveData<Boolean> confirmed = new MutableLiveData<>(false);
    public final MutableLiveData<String> smsCode = new MutableLiveData<>("");
    private final MediatorLiveData<Boolean> canSubmit = new MediatorLiveData<>();
    private final MutableLiveData<List<CancelOwnedFarmVO>> pickFarms = new MutableLiveData<>(Collections.emptyList());

    private final SingleLiveEvent<String> showFarmPicker = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> openFarmPage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> openExport = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showConfirmDialog = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> cancelled = new SingleLiveEvent<>();

    /** 当前要处理的动作：TRANSFER（F10）或 DISABLE（F05） */
    @Nullable
    private String pendingFarmAction;

    @Inject
    public CancelAccountViewModel(@NonNull AccountRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
        ownedFarms = Transformations.map(preview, p -> p == null ? Collections.emptyList() : p.getOwnedFarms());
        exitFarms = Transformations.map(preview, p -> p == null ? Collections.emptyList() : p.getExitFarms());
        recoveryBanner = Transformations.map(preview, CancelAccountViewModel::recoveryBannerOf);
        farmBanner = Transformations.map(preview, CancelAccountViewModel::farmBannerOf);
        showFarmActions = Transformations.map(preview, p -> p != null && hasDisposal(p, CancelOwnedFarmVO.DISPOSAL_TRANSFER_OR_DISABLE));
        showExport = Transformations.map(preview, p -> p != null && farmBannerOf(p) != null
                && !onlyDisposal(p, CancelOwnedFarmVO.DISPOSAL_WAIT_UNFREEZE));
        showConfirmArea = Transformations.map(preview, p -> p != null && p.isCanCancel());
        canSubmit.setValue(false);
        canSubmit.addSource(preview, v -> update());
        canSubmit.addSource(confirmed, v -> {
            clearFieldError(FIELD_CONFIRMED);
            onSubmitFieldChanged();
            update();
        });
        canSubmit.addSource(smsCode, v -> {
            clearFieldError(FIELD_SMS_CODE);
            onSubmitFieldChanged();
            update();
        });
        canSubmit.addSource(submitting, v -> update());
        load();
    }

    // ===== 文案换算 =====

    @Nullable
    static String recoveryBannerOf(@Nullable CancellationPreviewVO p) {
        if (p == null || p.getCancelAvailableDate() == null) {
            return null;
        }
        return "账号找回后 7 天内不能注销。" + DateFormatter.formatDate(p.getCancelAvailableDate()) + "起可以注销。";
    }

    @Nullable
    static String farmBannerOf(@Nullable CancellationPreviewVO p) {
        if (p == null) {
            return null;
        }
        List<String> active = namesOf(p, CancelOwnedFarmVO.DISPOSAL_TRANSFER_OR_DISABLE);
        if (!active.isEmpty()) {
            return String.join("、", active) + "仍在使用，请先转让给场长或停用后再注销。"
                    + "停用的养殖场数据保留 180 天，期间其他成员仍可查看和导出。";
        }
        List<String> frozen = namesOf(p, CancelOwnedFarmVO.DISPOSAL_WAIT_UNFREEZE);
        if (!frozen.isEmpty()) {
            return String.join("、", frozen) + "已被平台冻结，暂时不能注销。解冻后先转让给其他成员或停用，再注销。原因见消息中心。";
        }
        if (!namesOf(p, CancelOwnedFarmVO.DISPOSAL_RETAIN_180_DAYS).isEmpty() && p.isCanCancel()) {
            LocalDate d = DateFormatter.parseDate(p.getFarmCleanupDate());
            String date = d == null ? AccountForm.text(p.getFarmCleanupDate())
                    : d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
            return "已停用的养殖场数据保留 180 天，" + date + "后清理。"
                    + "期间其他成员仍可查看和导出，清理时他们会收到“养殖场已被删除”的消息。";
        }
        return null;
    }

    /** 清单第一行：“所有者 · 存栏 870 头”“所有者 · 待审核” */
    @NonNull
    public static String farmLine1(@NonNull CancelOwnedFarmVO f) {
        if (FarmContextVO.STATUS_NORMAL.equals(f.getFarmStatus()) && f.getCurrentStock() != null) {
            return "所有者 · 存栏 " + f.getCurrentStock() + " " + AccountForm.text(f.getCountUnitText());
        }
        return "所有者 · " + AccountForm.text(f.getFarmStatusText());
    }

    /** 清单第二行：处理方式；已发起转让时“已发起转让，待刘芳确认” */
    @NonNull
    public static String farmLine2(@NonNull CancelOwnedFarmVO f) {
        if (f.getTransferPendingToName() != null) {
            return "已发起转让，待" + f.getTransferPendingToName() + "确认";
        }
        return AccountForm.text(f.getDisposalText());
    }

    /** 第二行是否为 error 色（须先处理） */
    public static boolean farmLine2IsError(@NonNull CancelOwnedFarmVO f) {
        return f.isBlocking() || f.getTransferPendingToName() != null;
    }

    @NonNull
    private static List<String> namesOf(@NonNull CancellationPreviewVO p, @NonNull String disposal) {
        List<String> names = new ArrayList<>();
        for (CancelOwnedFarmVO f : p.getOwnedFarms()) {
            if (disposal.equals(f.getDisposal())) {
                names.add(AccountForm.text(f.getName()));
            }
        }
        return names;
    }

    private static boolean hasDisposal(@NonNull CancellationPreviewVO p, @NonNull String disposal) {
        return !namesOf(p, disposal).isEmpty();
    }

    private static boolean onlyDisposal(@NonNull CancellationPreviewVO p, @NonNull String disposal) {
        for (CancelOwnedFarmVO f : p.getOwnedFarms()) {
            if (f.isBlocking() && !disposal.equals(f.getDisposal())) {
                return false;
            }
        }
        return hasDisposal(p, disposal);
    }

    /** 确认对话框正文：注销后果 + 随注销删除 / 保留 180 天 / 将要退出的养殖场 */
    @NonNull
    String confirmMessage() {
        CancellationPreviewVO p = preview.getValue();
        StringBuilder sb = new StringBuilder("注销后立即生效，不能撤销，已登录的设备全部退出。");
        if (p == null) {
            return sb.toString();
        }
        List<String> parts = new ArrayList<>();
        List<String> deleted = namesOf(p, CancelOwnedFarmVO.DISPOSAL_DELETE_NOW);
        if (!deleted.isEmpty()) {
            parts.add(String.join("、", deleted) + "随注销一起删除");
        }
        List<String> retained = namesOf(p, CancelOwnedFarmVO.DISPOSAL_RETAIN_180_DAYS);
        if (!retained.isEmpty()) {
            parts.add(String.join("、", retained) + "的数据保留 180 天后清理");
        }
        List<String> exit = new ArrayList<>();
        for (CancelExitFarmVO f : p.getExitFarms()) {
            exit.add(AccountForm.text(f.getName()));
        }
        if (!exit.isEmpty()) {
            parts.add("你将退出" + String.join("、", exit));
        }
        if (!parts.isEmpty()) {
            sb.append(String.join("，", parts)).append("。");
        }
        return sb.toString();
    }

    private void update() {
        CancellationPreviewVO p = preview.getValue();
        String code = smsCode.getValue();
        Boolean v = p != null && p.isCanCancel() && Boolean.TRUE.equals(confirmed.getValue())
                && code != null && code.trim().length() == 6 && !isSubmitting();
        if (!v.equals(canSubmit.getValue())) {
            canSubmit.setValue(v);
        }
    }

    // ===== 对外 LiveData =====

    public LiveData<CancellationPreviewVO> getPreview() {
        return preview;
    }

    public LiveData<List<CancelOwnedFarmVO>> getOwnedFarms() {
        return ownedFarms;
    }

    public LiveData<List<CancelExitFarmVO>> getExitFarms() {
        return exitFarms;
    }

    public LiveData<String> getRecoveryBanner() {
        return recoveryBanner;
    }

    public LiveData<String> getFarmBanner() {
        return farmBanner;
    }

    public LiveData<Boolean> getShowFarmActions() {
        return showFarmActions;
    }

    public LiveData<Boolean> getShowExport() {
        return showExport;
    }

    public LiveData<Boolean> getShowConfirmArea() {
        return showConfirmArea;
    }

    public LiveData<Boolean> getCanSubmit() {
        return canSubmit;
    }

    public LiveData<List<CancelOwnedFarmVO>> getPickFarms() {
        return pickFarms;
    }

    public SingleLiveEvent<String> getShowFarmPicker() {
        return showFarmPicker;
    }

    public SingleLiveEvent<Long> getOpenFarmPage() {
        return openFarmPage;
    }

    public SingleLiveEvent<Boolean> getOpenExport() {
        return openExport;
    }

    public SingleLiveEvent<String> getShowConfirmDialog() {
        return showConfirmDialog;
    }

    public SingleLiveEvent<Boolean> getCancelled() {
        return cancelled;
    }

    @Nullable
    public String getPendingFarmAction() {
        return pendingFarmAction;
    }

    // ===== 方法 =====

    /** API-A19；勾选与验证码清空 */
    public void load() {
        confirmed.setValue(false);
        smsCode.setValue("");
        load(repository.getCancellationPreview(), preview::setValue);
    }

    @Override
    public void retry() {
        load();
    }

    public void sendSmsCode() {
        if (sms.isCounting()) {
            return;
        }
        launch(repository.sendAccountSmsCode(new AccountSmsCodeRequest(AccountSmsCodeRequest.SCENE_CANCEL_ACCOUNT)),
                vo -> onSmsSent(vo, FIELD_SMS_CODE), this::handleCancelError);
    }

    public void onTransferClick() {
        pendingFarmAction = ACTION_TRANSFER;
        List<CancelOwnedFarmVO> candidates = new ArrayList<>();
        for (CancelOwnedFarmVO f : ownedOrEmpty()) {
            if (CancelOwnedFarmVO.DISPOSAL_TRANSFER_OR_DISABLE.equals(f.getDisposal()) && f.isTransferable()
                    && f.getTransferPendingToName() == null) {
                candidates.add(f);
            }
        }
        dispatchCandidates(candidates, MSG_NO_TRANSFERABLE);
    }

    public void onDisableClick() {
        pendingFarmAction = ACTION_DISABLE;
        List<CancelOwnedFarmVO> candidates = new ArrayList<>();
        for (CancelOwnedFarmVO f : ownedOrEmpty()) {
            if (CancelOwnedFarmVO.DISPOSAL_TRANSFER_OR_DISABLE.equals(f.getDisposal())) {
                candidates.add(f);
            }
        }
        dispatchCandidates(candidates, null);
    }

    private void dispatchCandidates(@NonNull List<CancelOwnedFarmVO> candidates, @Nullable String emptyMessage) {
        if (candidates.isEmpty()) {
            if (emptyMessage != null) {
                showMessage(emptyMessage);
            }
            return;
        }
        if (candidates.size() == 1) {
            onFarmPicked(candidates.get(0).getFarmId());
            return;
        }
        pickFarms.setValue(candidates);
        showFarmPicker.setValue(pendingFarmAction);
    }

    @NonNull
    private List<CancelOwnedFarmVO> ownedOrEmpty() {
        CancellationPreviewVO p = preview.getValue();
        return p == null ? Collections.emptyList() : p.getOwnedFarms();
    }

    /** 选定养殖场：不是当前养殖场时先切换（IFarmContextService.switchTo，API-F03），再打开 F10 / F05 */
    public void onFarmPicked(long farmId) {
        Long current = farmContext.currentFarmId();
        if (current != null && current == farmId) {
            openFarmPage.setValue(farmId);
            return;
        }
        String name = null;
        for (CancelOwnedFarmVO f : ownedOrEmpty()) {
            if (f.getFarmId() == farmId) {
                name = f.getName();
            }
        }
        final String farmName = name;
        launch(repository.restoreFarm(farmId), () -> {
            if (farmName != null) {
                showMessage("已切换到" + farmName);
            }
            openFarmPage.setValue(farmId);
        });
    }

    public void onExportClick() {
        openExport.setValue(true);
    }

    public void onCancelClick() {
        CancellationPreviewVO p = preview.getValue();
        if (p == null || !p.isCanCancel() || isSubmitting()) {
            return;
        }
        FormValidator v = new FormValidator()
                .check(FIELD_CONFIRMED, Boolean.TRUE.equals(confirmed.getValue()), AccountForm.MSG_CANCEL_CONFIRM)
                .smsCode(FIELD_SMS_CODE, smsCode.getValue());
        if (!applyValidation(v.validate())) {
            return;
        }
        showConfirmDialog.setValue(confirmMessage());
    }

    /** 确认对话框点“注销” */
    public void confirmCancel() {
        CancellationPreviewVO p = preview.getValue();
        if (p == null || isSubmitting()) {
            return;
        }
        String key = obtainIdemKey();
        submitting.setValue(true);
        AccountCancelRequest req = new AccountCancelRequest(AccountForm.trim(smsCode.getValue()),
                AccountForm.text(p.getChecklistVersion()), Boolean.TRUE.equals(confirmed.getValue()));
        launch(repository.cancelAccount(req, key), () -> {
            onSubmitFinished(null);
            cancelled.setValue(true);
        }, e -> {
            onSubmitFinished(e);
            handleCancelError(e);
        });
    }

    private void handleCancelError(@NonNull ApiException e) {
        int code = e.getCode();
        if (code == ErrorCodes.CANCEL_NOT_ALLOWED || code == ErrorCodes.VERSION_CONFLICT) {
            showMessage(e.getMessage());
            load();
            return;
        }
        if (handleSmsError(e, FIELD_SMS_CODE)) {
            return;
        }
        handleError(e);
    }

    @Override
    protected void onDataChanged(@NonNull ApiException e) {
        load();
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        showMessage(e.getMessage());
    }
}
