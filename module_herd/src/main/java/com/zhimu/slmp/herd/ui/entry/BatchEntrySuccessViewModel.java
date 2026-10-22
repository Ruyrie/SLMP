package com.zhimu.slmp.herd.ui.entry;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;

import com.google.gson.Gson;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.model.vo.BatchEntryResultVO;
import com.zhimu.slmp.herd.ui.common.HerdFormat;
import com.zhimu.slmp.herd.ui.common.NavTarget;
import com.zhimu.slmp.herd.util.QrBitmapEncoder;
import com.zhimu.slmp.herd.util.QrLabelRenderer;

import javax.inject.Inject;

/**
 * L09 入栏成功：批次号、二维码（本地由 qr_content 生成）、入栏摘要与超出标签；保存图片、打印标签、查看档案。不再请求接口。
 */
public class BatchEntrySuccessViewModel extends BaseViewModel {

    public static final String MSG_SAVED = "二维码已保存到相册";
    public static final String MSG_SAVE_FAILED = "保存失败，请重试";
    private static final int QR_SIZE_PX = 600;

    private final Context context;
    private final Gson gson;

    private final MutableLiveData<BatchEntryResultVO> result = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> qrBitmap = new MutableLiveData<>();
    private final LiveData<String> summaryText;
    private final LiveData<String> entryText;
    private final LiveData<String> penStockText;
    private final LiveData<String> overTag;
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final SingleLiveEvent<Bitmap> printEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<NavTarget> navEvent = new SingleLiveEvent<>();

    @Inject
    public BatchEntrySuccessViewModel(@NonNull Context context, @NonNull Gson gson) {
        this.context = context.getApplicationContext();
        this.gson = gson;
        summaryText = Transformations.map(result, r -> r == null ? "" : r.getPenName() + "入栏 "
                + HerdFormat.qty(r.getEntryCount(), r.getUnitText()) + "，可以开始登记投喂和称重");
        entryText = Transformations.map(result, r -> r == null ? "" : DateFormatter.formatDate(r.getEntryDate(), false)
                + HerdFormat.DOT + HerdFormat.qty(r.getEntryCount(), r.getUnitText()) + HerdFormat.DOT
                + r.getEntryAgeDays() + " 日龄");
        penStockText = Transformations.map(result, r -> r == null ? ""
                : HerdFormat.stockSlashCapacity(r.getPenStockAfter(), r.getCapacity(), r.getUnitText()));
        overTag = Transformations.map(result, r -> r == null || r.getOverCapacityCount() <= 0 ? null
                : "超出 " + HerdFormat.qty(r.getOverCapacityCount(), r.getUnitText()));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        init(handle.get(HerdExtras.EXTRA_RESULT_JSON));
    }

    public void init(@Nullable String resultJson) {
        if (resultJson == null) {
            return;
        }
        BatchEntryResultVO r = gson.fromJson(resultJson, BatchEntryResultVO.class);
        result.setValue(r);
        if (r != null && r.getQrContent() != null) {
            launch(QrBitmapEncoder.encodeAsync(r.getQrContent(), QR_SIZE_PX), qrBitmap::setValue, e -> {
            });
        }
    }

    public LiveData<BatchEntryResultVO> getResult() {
        return result;
    }

    public LiveData<Bitmap> getQrBitmap() {
        return qrBitmap;
    }

    public LiveData<String> getSummaryText() {
        return summaryText;
    }

    public LiveData<String> getEntryText() {
        return entryText;
    }

    public LiveData<String> getPenStockText() {
        return penStockText;
    }

    public LiveData<String> getOverTag() {
        return overTag;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public SingleLiveEvent<Bitmap> getPrintEvent() {
        return printEvent;
    }

    public SingleLiveEvent<NavTarget> getNavEvent() {
        return navEvent;
    }

    /** 标签图片（二维码 + 批次号 + label_text）写入系统相册 */
    public void saveLabel() {
        BatchEntryResultVO r = result.getValue();
        if (r == null || Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        launch(QrLabelRenderer.renderAsync(r.getQrContent(), r.getBatchNo(), r.getLabelText())
                .flatMapCompletable(bmp -> QrLabelRenderer.saveToGallery(context, bmp, r.getBatchNo()))
                .toSingleDefault(true), ok -> {
            saving.setValue(false);
            showMessage(MSG_SAVED);
        }, e -> {
            saving.setValue(false);
            showMessage(MSG_SAVE_FAILED);
        });
    }

    /** “打印标签”：生成标签位图交给 PrintManager */
    public void printLabel() {
        BatchEntryResultVO r = result.getValue();
        if (r == null) {
            return;
        }
        launch(QrLabelRenderer.renderAsync(r.getQrContent(), r.getBatchNo(), r.getLabelText()), printEvent::setValue,
                e -> showMessage(MSG_SAVE_FAILED));
    }

    /** “查看档案”：打开新批次 L06 并关闭本页 */
    public void openArchive() {
        BatchEntryResultVO r = result.getValue();
        if (r != null) {
            navEvent.setValue(NavTarget.of(RouterPath.L06).put(RouterPath.EXTRA_BATCH_ID, r.getBatchId()).finishCurrent());
        }
    }
}
