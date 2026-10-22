package com.zhimu.slmp.base.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.UiMessage;

/**
 * Fragment 基类：观察 ViewModel 的一次性提示并用 Snackbar 显示，提供 ViewModel 获取辅助。
 * observeBase 在 onViewCreated 中调用（使用 viewLifecycleOwner）。
 */
public abstract class BaseFragment extends Fragment {

    public BaseFragment() {
        super();
    }

    public BaseFragment(int contentLayoutId) {
        super(contentLayoutId);
    }

    /** Fragment 范围的 ViewModel（SavedStateHandle 中带 Fragment arguments） */
    protected <T extends ViewModel> T obtainViewModel(@NonNull ViewModelProvider.Factory factory,
                                                      @NonNull Class<T> cls) {
        return new ViewModelProvider(this, factory).get(cls);
    }

    /** 宿主 Activity 范围的 ViewModel（底部面板、对话框与所在页面共用） */
    protected <T extends ViewModel> T obtainActivityViewModel(@NonNull ViewModelProvider.Factory factory,
                                                              @NonNull Class<T> cls) {
        return new ViewModelProvider(requireActivity(), factory).get(cls);
    }

    protected void observeBase(@NonNull BaseViewModel vm) {
        vm.getMessage().observe(getViewLifecycleOwner(), this::showMessage);
    }

    public void showMessage(@Nullable UiMessage message) {
        if (message == null || getView() == null) {
            return;
        }
        SnackbarHelper.show(requireView(), message, getSnackbarAnchor(), this::onMessageAction);
    }

    public void showMessage(@NonNull String text) {
        showMessage(UiMessage.of(text));
    }

    protected void onMessageAction(@NonNull String actionId) {
    }

    /** 锚点：本 Fragment 布局中带 ANCHOR_TAG 的视图，否则取宿主 BaseActivity 的锚点（例如底部导航） */
    @Nullable
    protected View getSnackbarAnchor() {
        View own = SnackbarHelper.findAnchor(getView());
        if (own != null) {
            return own;
        }
        if (getActivity() instanceof BaseActivity) {
            return ((BaseActivity) getActivity()).getSnackbarAnchor();
        }
        return null;
    }
}
