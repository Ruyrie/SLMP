package com.zhimu.slmp.herd.ui.scan;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.herd.HerdExtras;
import com.zhimu.slmp.herd.R;
import com.zhimu.slmp.herd.databinding.HerdActivityBatchScanBinding;
import com.zhimu.slmp.herd.di.HerdInjector;
import com.zhimu.slmp.herd.model.vo.BatchLookupVO;
import com.zhimu.slmp.herd.ui.trace.BatchTraceActivity;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.camera.CameraContract;
import com.zhimu.slmp.media.camera.CameraResult;

/**
 * L07 扫码查看档案：打开 lib_media 相机页扫码模式（取景框、扫描线、手电筒、相机权限与“输入批次号”入口由相机页提供），
 * 识别原文交给 BatchScanViewModel；识别到有权查看的批次后打开 L06 并替换本页。
 */
public class BatchScanActivity extends BaseActivity {

    private static final String REQ_NO_PERMISSION = "herd_scan_no_permission";
    private static final String STATE_STARTED = "herd_scan_started";

    private HerdActivityBatchScanBinding binding;
    private BatchScanViewModel vm;

    private final ActivityResultLauncher<CameraArgs> camera = registerForActivityResult(new CameraContract(),
            this::onCameraResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = HerdActivityBatchScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = obtainViewModel(HerdInjector.factory(), BatchScanViewModel.class);
        observeBase(vm);

        binding.herdToolbar.setNavigationOnClickListener(v -> finish());
        ClickThrottle.setOnClickListener(binding.herdResume, v -> vm.resumeScan());
        ClickThrottle.setOnClickListener(binding.herdInput, v -> vm.openInput());
        vm.getLookupLoading().observe(this, loading -> renderState());
        vm.getAnalyzing().observe(this, a -> renderState());
        vm.getScanEvent().observe(this, v -> launchCamera());
        vm.getInputEvent().observe(this, v -> new BatchNoInputBottomSheet().showSafely(getSupportFragmentManager()));
        vm.getOpenArchiveEvent().observe(this, this::openArchive);
        vm.getOpenPublicTraceEvent().observe(this, token -> {
            startActivity(new Intent(this, BatchTraceActivity.class).putExtra(HerdExtras.EXTRA_TRACE_TOKEN, token));
            finish();
        });
        vm.getNoPermissionEvent().observe(this, message -> ConfirmDialogFragment.builder(REQ_NO_PERMISSION)
                .icon(com.zhimu.slmp.base.R.drawable.ic_lock)
                .title(getString(R.string.herd_scan_no_permission_title))
                .message(getString(R.string.herd_scan_no_permission_message))
                .negative(getString(R.string.herd_scan_back))
                .positive(getString(R.string.herd_scan_resume))
                .cancelOnTouchOutside(false)
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_NO_PERMISSION, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.resumeScan();
            } else {
                finish();
            }
        });

        boolean started = savedInstanceState != null && savedInstanceState.getBoolean(STATE_STARTED);
        if (!started) {
            vm.resumeScan();
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_STARTED, true);
    }

    private void launchCamera() {
        camera.launch(CameraArgs.scan()
                .title(getString(R.string.herd_scan_title))
                .scanHint(getString(R.string.herd_scan_hint), getString(R.string.herd_scan_sub_hint))
                .manualInput(getString(R.string.herd_scan_manual), getString(R.string.herd_scan_manual_hint)));
    }

    private void onCameraResult(CameraResult result) {
        switch (result.getType()) {
            case CameraResult.TYPE_SCAN:
                vm.onQrDecoded(result.getScanText());
                break;
            case CameraResult.TYPE_MANUAL_INPUT:
                vm.openInput();
                break;
            default:
                // 在相机页按返回：关闭扫码页，回到扫码前的页面
                finish();
                break;
        }
    }

    private void renderState() {
        boolean loading = Boolean.TRUE.equals(vm.getLookupLoading().getValue());
        boolean analyzing = Boolean.TRUE.equals(vm.getAnalyzing().getValue());
        binding.herdLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.herdPaused.setVisibility(!loading && !analyzing ? View.VISIBLE : View.GONE);
    }

    /** 打开 L06 并替换本页；跨场打开时带上原养殖场（“切回”）与批次所属养殖场名称（养殖场标签） */
    private void openArchive(BatchLookupVO vo) {
        Bundle extras = new Bundle();
        if (vo.getBatchId() != null) {
            extras.putLong(RouterPath.EXTRA_BATCH_ID, vo.getBatchId());
        }
        if (BatchLookupVO.SWITCH_FARM.equals(vo.getResult()) && vm.getSwitchedFromFarmId() != null) {
            extras.putLong(RouterPath.EXTRA_FROM_FARM_ID, vm.getSwitchedFromFarmId());
            extras.putString(RouterPath.EXTRA_FROM_FARM_NAME, vm.getSwitchedFromFarmName());
            extras.putString(RouterPath.EXTRA_CROSS_FARM_NAME, vo.getFarmName());
        }
        if (Router.open(this, RouterPath.L06, extras)) {
            finish();
        }
    }
}
