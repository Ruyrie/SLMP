package com.zhimu.slmp.media.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 打开相机页 I08 并接收结果（拍照路径、扫码原文或“手动输入”）。
 * <pre>
 * private final ActivityResultLauncher&lt;CameraArgs&gt; camera =
 *         registerForActivityResult(new CameraContract(), result -&gt; {
 *             if (result.getType() == CameraResult.TYPE_SCAN) vm.onQrDecoded(result.getScanText());
 *             else if (result.getType() == CameraResult.TYPE_MANUAL_INPUT) vm.openBatchNoInput();
 *         });
 * camera.launch(CameraArgs.scan().title("扫码查看档案").manualInput("输入批次号", "二维码模糊或破损时，也可以输入批次号"));
 * </pre>
 */
public class CameraContract extends ActivityResultContract<CameraArgs, CameraResult> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull CameraArgs input) {
        return new Intent(RouterPath.I08).setPackage(context.getPackageName()).putExtras(input.toBundle());
    }

    @NonNull
    @Override
    public CameraResult parseResult(int resultCode, @Nullable Intent intent) {
        if (resultCode != Activity.RESULT_OK) {
            return CameraResult.canceled();
        }
        return CameraResult.fromIntent(intent);
    }
}
