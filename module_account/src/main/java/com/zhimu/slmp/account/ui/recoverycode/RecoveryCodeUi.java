package com.zhimu.slmp.account.ui.recoverycode;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.R;
import com.zhimu.slmp.account.databinding.AccountIncludeRecoveryCodeBinding;
import com.zhimu.slmp.account.util.RecoveryCodeImageSaver;
import com.zhimu.slmp.account.widget.RecoveryCodeView;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.common.model.vo.RecoveryCodeVO;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 恢复码内容区（A04、A06 第 2 步）的界面辅助：绑定说明与恢复码、复制到剪贴板（标记敏感）、保存图片到相册。
 */
public final class RecoveryCodeUi {

    private RecoveryCodeUi() {
    }

    /**
     * @param showRegenerateItem 用途列表是否显示“可以重新生成”（A08 重新生成版只保留“找回账号时使用”）
     */
    public static void bindBody(@NonNull LifecycleOwner owner, @NonNull AccountIncludeRecoveryCodeBinding body,
                                @NonNull LiveData<String> desc, @NonNull LiveData<String> groups,
                                @NonNull MutableLiveData<Boolean> saved, @NonNull LiveData<Boolean> showRegenerateItem,
                                @NonNull Runnable onCopy, @NonNull Runnable onSave) {
        desc.observe(owner, body.accountCodeDesc::setText);
        groups.observe(owner, body.accountCodeValue::setCode);
        showRegenerateItem.observe(owner, show ->
                body.accountCodeUseRegenerate.setVisibility(Boolean.FALSE.equals(show) ? View.GONE : View.VISIBLE));
        saved.observe(owner, checked -> {
            boolean c = Boolean.TRUE.equals(checked);
            if (body.accountCodeSaved.isChecked() != c) {
                body.accountCodeSaved.setChecked(c);
            }
        });
        body.accountCodeSaved.setOnCheckedChangeListener((b, isChecked) -> {
            if (!Boolean.valueOf(isChecked).equals(saved.getValue())) {
                saved.setValue(isChecked);
            }
        });
        ClickThrottle.setOnClickListener(body.accountCodeCopy, v -> onCopy.run());
        ClickThrottle.setOnClickListener(body.accountCodeSave, v -> onSave.run());
    }

    /**
     * 写剪贴板并标记敏感内容；Android 13 及以上系统会显示自己的剪贴板提示，返回 false 表示不再弹 Snackbar。
     */
    public static boolean copy(@NonNull Context context, @NonNull String code) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return false;
        }
        ClipData clip = ClipData.newPlainText(context.getString(R.string.account_code_clip_label), code);
        PersistableBundle extras = new PersistableBundle();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        } else {
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        }
        clip.getDescription().setExtras(extras);
        cm.setPrimaryClip(clip);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
    }

    /** io 线程绘制位图并写入 Pictures/智牧通，主线程回调 */
    @NonNull
    public static Disposable saveImage(@NonNull Context context, @NonNull RecoveryCodeVO vo,
                                       @NonNull Consumer<Uri> onSaved, @NonNull Runnable onFailed) {
        Context app = context.getApplicationContext();
        return Single.fromCallable(() -> RecoveryCodeImageSaver.save(app, RecoveryCodeView.createBitmap(app, vo)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onSaved, e -> onFailed.run());
    }

    /** “查看”：用 ACTION_VIEW 打开已保存的图片 */
    public static void view(@NonNull Context context, @Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (RuntimeException ignored) {
            // 没有可打开图片的应用
        }
    }
}
