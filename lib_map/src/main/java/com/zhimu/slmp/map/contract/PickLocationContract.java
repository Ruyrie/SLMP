package com.zhimu.slmp.map.contract;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;

import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.PickLocationInput;

/**
 * 打开 F04 养殖场位置选点并取回 LocationResult（按返回放弃时为 null，原地址不变）。
 * <pre>
 * launcher = registerForActivityResult(new PickLocationContract(), result -&gt; { if (result != null) vm.onLocationPicked(result); });
 * launcher.launch(new PickLocationInput(PickLocationInput.MODE_NEW, "北湾牛场", null));
 * </pre>
 */
public class PickLocationContract extends ActivityResultContract<PickLocationInput, LocationResult> {

    /** 启动参数（PickLocationInput） */
    public static final String EXTRA_INPUT = "pick_location_input";
    /** 结果（LocationResult） */
    public static final String EXTRA_RESULT = "pick_location_result";

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, PickLocationInput input) {
        return new Intent(RouterPath.F04)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_INPUT, input);
    }

    @Nullable
    @Override
    public LocationResult parseResult(int resultCode, @Nullable Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null;
        }
        return IntentCompat.getParcelableExtra(intent, EXTRA_RESULT, LocationResult.class);
    }
}
