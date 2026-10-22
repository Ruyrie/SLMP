package com.zhimu.slmp.common.app;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zhimu.slmp.common.R;

/**
 * 需要更新（第 5 级对话框，03 号文档 12.6 节）：低于最低支持版本时强制更新，不可关闭；“去更新”用浏览器打开下载地址。
 * 下载管理与安装引导在后续版本实现。
 */
public class UpdateDialogFragment extends DialogFragment {

    private static final String TAG = "common_core_update_dialog";
    private static final String ARG_MIN_VERSION = "min_version";
    private static final String ARG_URL = "download_url";

    public static void show(@NonNull FragmentActivity activity, @NonNull String minVersion, @Nullable String url) {
        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.isStateSaved() || fm.findFragmentByTag(TAG) != null) {
            return;
        }
        UpdateDialogFragment f = new UpdateDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MIN_VERSION, minVersion);
        args.putString(ARG_URL, url);
        f.setArguments(args);
        f.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        setCancelable(false);
        Bundle args = requireArguments();
        String min = args.getString(ARG_MIN_VERSION, "");
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.common_core_update_title)
                .setMessage(getString(R.string.common_core_update_message, min))
                .setPositiveButton(R.string.common_core_update_action, null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        // 点“去更新”后对话框保持显示（强制更新）
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> openDownload(args.getString(ARG_URL))));
        return dialog;
    }

    private void openDownload(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // 没有浏览器时不处理
        }
    }
}
