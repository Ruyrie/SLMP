package com.zhimu.slmp.herd.ui.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.service.IFarmContextService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.herd.data.BatchRepository;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.util.BatchNoValidator;

import javax.inject.Inject;

/**
 * L07 扫码查看档案：扫码由 lib_media 相机页（CameraContract 扫码模式）完成，原文交给 API-L14（qr_content，待定事项 1）；
 * 也可输入批次号。按 result 分发：OK 打开 L06；SWITCH_FARM 先经 IFarmContextService.switchTo 切换再打开；
 * NO_PERMISSION 弹“无权查看”；NOT_BATCH_QR、扫码 NOT_FOUND 提示后可继续扫码；输入时 NOT_FOUND、INVALID_FORMAT 写入输入框。
 */
public class BatchScanViewModel extends BaseViewModel {

    public static final String MSG_NOT_BATCH_QR = "没有识别到批次二维码，请对准批次标签重试";
    public static final String MSG_SWITCH_UNAVAILABLE = "暂时无法切换到%s，请从养殖场切换器切换后再查看";
    /** 2 秒内相同内容忽略 */
    static final long DUPLICATE_WINDOW_MS = 2000;
    /** qr_content 最多 200 字，超出按非批次二维码提示 */
    static final int QR_MAX_LENGTH = 200;

    private final BatchRepository repository;
    private final FarmContext farmContext;

    /** 是否处于识别中（相机页打开）；识别到结果或弹出对话框后为 false */
    private final MutableLiveData<Boolean> analyzing = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> lookupLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> batchNoInput = new MutableLiveData<>("");
    private final MutableLiveData<String> batchNoError = new MutableLiveData<>();
    private final SingleLiveEvent<Void> scanEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> inputEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<BatchLookupVO> openArchiveEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> openPublicTraceEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> noPermissionEvent = new SingleLiveEvent<>();

    @Nullable
    private String lastContent;
    private long lastDecodedAt;
    @Nullable
    private Long switchedFromFarmId;
    @Nullable
    private String switchedFromFarmName;

    @Inject
    public BatchScanViewModel(@NonNull BatchRepository repository, @NonNull FarmContext farmContext) {
        this.repository = repository;
        this.farmContext = farmContext;
    }

    // ===== 对外 =====

    public LiveData<Boolean> getAnalyzing() {
        return analyzing;
    }

    public LiveData<Boolean> getLookupLoading() {
        return lookupLoading;
    }

    /** 输入面板（双向） */
    public MutableLiveData<String> getBatchNoInput() {
        return batchNoInput;
    }

    public LiveData<String> getBatchNoError() {
        return batchNoError;
    }

    /** 打开相机页扫码 */
    public SingleLiveEvent<Void> getScanEvent() {
        return scanEvent;
    }

    /** 打开“输入批次号”面板 */
    public SingleLiveEvent<Void> getInputEvent() {
        return inputEvent;
    }

    public SingleLiveEvent<BatchLookupVO> getOpenArchiveEvent() {
        return openArchiveEvent;
    }

    public SingleLiveEvent<String> getOpenPublicTraceEvent() {
        return openPublicTraceEvent;
    }

    public SingleLiveEvent<String> getNoPermissionEvent() {
        return noPermissionEvent;
    }

    /** 跨场打开时扫码前所在的养殖场（L06“切回”） */
    @Nullable
    public Long getSwitchedFromFarmId() {
        return switchedFromFarmId;
    }

    @Nullable
    public String getSwitchedFromFarmName() {
        return switchedFromFarmName;
    }

    // ===== 扫码 =====

    /** 相机页返回的扫码原文 */
    public void onQrDecoded(@Nullable String content) {
        if (content == null || Boolean.TRUE.equals(lookupLoading.getValue())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (content.equals(lastContent) && now - lastDecodedAt < DUPLICATE_WINDOW_MS) {
            // 2 秒内相同内容忽略，继续识别
            resumeScan();
            return;
        }
        lastContent = content;
        lastDecodedAt = now;
        analyzing.setValue(false);
        if (content.length() > QR_MAX_LENGTH) {
            showMessage(MSG_NOT_BATCH_QR);
            return;
        }
        lookupLoading.setValue(true);
        launch(repository.lookupByQr(content), vo -> {
            lookupLoading.setValue(false);
            onLookupResult(vo, false);
        }, e -> {
            lookupLoading.setValue(false);
            handleError(e);
        });
    }

    /** 输入面板“打开档案”：去空格转大写后本地校验格式，再调 API-L14 */
    public void submitBatchNo() {
        if (Boolean.TRUE.equals(lookupLoading.getValue())) {
            return;
        }
        String no = BatchNoValidator.normalize(batchNoInput.getValue());
        String error = BatchNoValidator.validate(no);
        batchNoError.setValue(error);
        if (error != null) {
            return;
        }
        analyzing.setValue(false);
        lookupLoading.setValue(true);
        launch(repository.lookupByNo(no), vo -> {
            lookupLoading.setValue(false);
            onLookupResult(vo, true);
        }, e -> {
            lookupLoading.setValue(false);
            handleError(e);
        });
    }

    /** 按 result 分发 */
    public void onLookupResult(@NonNull BatchLookupVO vo, boolean fromInput) {
        String result = vo.getResult();
        if (BatchLookupVO.OK.equals(result)) {
            switchedFromFarmId = null;
            switchedFromFarmName = null;
            openArchiveEvent.setValue(vo);
        } else if (BatchLookupVO.SWITCH_FARM.equals(result)) {
            switchFarmThenOpen(vo);
        } else if (BatchLookupVO.PUBLIC_TRACE.equals(result)) {
            // Only scanning a shareable token opens public data. Typing a batch number never grants access.
            String token = vo.getTraceToken();
            if (!fromInput && token != null && !token.trim().isEmpty()) {
                switchedFromFarmId = null;
                switchedFromFarmName = null;
                openPublicTraceEvent.setValue(token);
            } else if (fromInput) {
                noPermissionEvent.setValue("请扫描该批次的公开二维码查看溯源信息");
            } else {
                showMessage(MSG_NOT_BATCH_QR);
            }
        } else if (BatchLookupVO.NO_PERMISSION.equals(result)) {
            noPermissionEvent.setValue(vo.getMessage());
        } else if (fromInput) {
            // 输入时 NOT_FOUND、INVALID_FORMAT 等写入输入框错误态
            batchNoError.setValue(vo.getMessage() != null ? vo.getMessage() : BatchNoValidator.MSG_INVALID);
        } else {
            // 扫码：NOT_BATCH_QR、NOT_FOUND 提示后可继续识别
            showMessage(vo.getMessage() != null ? vo.getMessage() : MSG_NOT_BATCH_QR);
        }
    }

    /** SWITCH_FARM：先切换当前养殖场（API-F03）再打开档案；服务未注册（例如单独运行）时提示 */
    private void switchFarmThenOpen(@NonNull BatchLookupVO vo) {
        Long farmId = vo.getFarmId();
        IFarmContextService service = ServiceHub.get(IFarmContextService.class);
        if (service == null || farmId == null) {
            showMessage(String.format(MSG_SWITCH_UNAVAILABLE, vo.getFarmName() == null ? "该养殖场" : vo.getFarmName()));
            return;
        }
        FarmContextVO current = farmContext.current();
        Long fromId = current == null ? null : current.getFarmId();
        String fromName = current == null ? null : current.getName();
        lookupLoading.setValue(true);
        launch(service.switchTo(farmId), ctx -> {
            lookupLoading.setValue(false);
            switchedFromFarmId = fromId;
            switchedFromFarmName = fromName;
            openArchiveEvent.setValue(vo);
        }, e -> {
            lookupLoading.setValue(false);
            handleError(e);
        });
    }

    /** 恢复识别（“继续扫码”、Snackbar 后） */
    public void resumeScan() {
        analyzing.setValue(true);
        batchNoError.setValue(null);
        scanEvent.call();
    }

    /** “输入批次号” */
    public void openInput() {
        analyzing.setValue(false);
        inputEvent.call();
    }

    /** 输入内容变化：清除错误态 */
    public void onBatchNoChanged(@Nullable String text) {
        batchNoInput.setValue(text);
        if (batchNoError.getValue() != null) {
            batchNoError.setValue(null);
        }
    }
}
