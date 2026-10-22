package com.zhimu.slmp.herd.ui.archive;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.BaseBottomSheetDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.herd.databinding.HerdSheetBatchQrBinding;
import com.zhimu.slmp.herd.model.vo.BatchDetailVO;
import com.zhimu.slmp.herd.util.LabelPrintAdapter;
import com.zhimu.slmp.herd.util.QrBitmapEncoder;
import com.zhimu.slmp.herd.util.QrLabelRenderer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * L06 批次二维码底部面板：二维码由 qr_content 本地生成；“保存图片”写入系统相册，“打印标签”调起系统打印。
 */
public class BatchQrBottomSheet extends BaseBottomSheetDialogFragment {

    public static final String MSG_SAVED = "二维码已保存到相册";
    public static final String MSG_SAVE_FAILED = "保存失败，请重试";

    private static final String ARG_NO = "batch_no";
    private static final String ARG_QR = "qr_content";
    private static final String ARG_LABEL = "label_text";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private HerdSheetBatchQrBinding binding;

    @NonNull
    public static BatchQrBottomSheet newInstance(@NonNull BatchDetailVO d) {
        Bundle args = baseArgs(null, null, null, null);
        args.putString(ARG_NO, d.getBatchNo());
        args.putString(ARG_QR, d.getQrContent());
        args.putString(ARG_LABEL, d.getLabelText());
        BatchQrBottomSheet f = new BatchQrBottomSheet();
        f.setArguments(args);
        return f;
    }

    @Override
    protected boolean expandOnShow() {
        return true;
    }

    @Nullable
    @Override
    protected View onCreateContentView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = HerdSheetBatchQrBinding.inflate(inflater, container, false);
        Bundle args = requireArguments();
        String no = args.getString(ARG_NO, "");
        String qr = args.getString(ARG_QR, "");
        String label = args.getString(ARG_LABEL);
        binding.herdQrBatchNo.setText(no);
        binding.herdQrLabel.setText(label);
        int size = getResources().getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_12) * 4;
        if (!qr.isEmpty()) {
            disposables.add(QrBitmapEncoder.encodeAsync(qr, size)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(binding.herdQrImage::setImageBitmap, t -> {
                    }));
        }
        ClickThrottle.setOnClickListener(binding.herdQrSave, v -> save(qr, no, label));
        ClickThrottle.setOnClickListener(binding.herdQrPrint, v -> print(qr, no, label));
        return binding.getRoot();
    }

    private void save(String qr, String no, @Nullable String label) {
        ButtonLoading.setLoading(binding.herdQrSave, true);
        disposables.add(QrLabelRenderer.renderAsync(qr, no, label)
                .flatMapCompletable(bmp -> QrLabelRenderer.saveToGallery(requireContext(), bmp, no))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    ButtonLoading.setLoading(binding.herdQrSave, false);
                    notify(MSG_SAVED);
                }, t -> {
                    ButtonLoading.setLoading(binding.herdQrSave, false);
                    notify(MSG_SAVE_FAILED);
                }));
    }

    private void print(String qr, String no, @Nullable String label) {
        disposables.add(QrLabelRenderer.renderAsync(qr, no, label)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bmp -> LabelPrintAdapter.print(requireActivity(), bmp, no), t -> notify(MSG_SAVE_FAILED)));
    }

    private void notify(@NonNull String text) {
        Activity a = getActivity();
        if (a instanceof BaseActivity) {
            ((BaseActivity) a).showMessage(text);
        }
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        super.onDestroyView();
    }
}
