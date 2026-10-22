package com.zhimu.slmp.base.dialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * 弹窗守卫（02 号《UI设计规范》8.1 节、03 号文档 10.2 节）：同一时刻屏幕上最多一个弹窗，弹窗不叠弹窗。
 * <p>
 * 显示前检查：页面状态已保存时不显示（避免 IllegalStateException）；同标签的弹窗已存在时不重复显示（防连点）；
 * 当前 FragmentManager 及其子 Fragment 中已有正在显示的底部面板或对话框时不显示。
 * 底部面板中的操作需要确认时，先 {@code dismiss()} 面板再显示对话框（正在移除的弹窗不计入）。
 * 唯一例外是底部面板中的日期或时间字段（{@link DateTimePicker} 只检查同标签）。
 * <p>
 * 显示使用 {@code showNow}，返回后立即可被查到，连续两次点击只会弹出一个。
 */
public final class DialogGuard {

    private DialogGuard() {
    }

    /** 能否显示：状态未保存、同标签不存在、没有其他正在显示的弹窗 */
    public static boolean canShow(@NonNull FragmentManager fm, @NonNull String tag) {
        return canShowIgnoringOthers(fm, tag) && !hasShowingDialog(fm);
    }

    /** 只检查状态与同标签（用于底部面板中的日期、时间选择器） */
    public static boolean canShowIgnoringOthers(@NonNull FragmentManager fm, @NonNull String tag) {
        if (fm.isStateSaved() || fm.isDestroyed()) {
            return false;
        }
        Fragment existing = fm.findFragmentByTag(tag);
        return existing == null || existing.isRemoving();
    }

    /** fm 及其子 Fragment 中是否有正在显示（未在移除）的弹窗 */
    public static boolean hasShowingDialog(@NonNull FragmentManager fm) {
        for (Fragment f : fm.getFragments()) {
            if (!f.isAdded() || f.isRemoving()) {
                continue;
            }
            if (f instanceof DialogFragment && ((DialogFragment) f).getShowsDialog()) {
                return true;
            }
            if (hasShowingDialog(f.getChildFragmentManager())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按守卫规则显示弹窗。
     *
     * @return 是否已显示；false 表示被拦截（同标签已存在、已有其他弹窗或页面状态已保存）
     */
    public static boolean show(@NonNull DialogFragment dialog, @NonNull FragmentManager fm, @NonNull String tag) {
        if (!canShow(fm, tag)) {
            return false;
        }
        dialog.showNow(fm, tag);
        return true;
    }

    /** 只检查同标签后显示（日期、时间选择器可以叠在底部面板之上） */
    static boolean showAllowingStack(@NonNull DialogFragment dialog, @NonNull FragmentManager fm, @NonNull String tag) {
        if (!canShowIgnoringOthers(fm, tag)) {
            return false;
        }
        dialog.showNow(fm, tag);
        return true;
    }
}
