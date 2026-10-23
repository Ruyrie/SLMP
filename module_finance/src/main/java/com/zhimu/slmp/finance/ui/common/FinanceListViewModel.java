package com.zhimu.slmp.finance.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseListViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.NavTarget;

/**
 * 模块10 分页列表 ViewModel 基类（E04、E09、E11、E12、E14）：与 {@link FinanceViewModel} 相同的员工拦截、40303 文案与跳转事件。
 */
public abstract class FinanceListViewModel<F, T> extends BaseListViewModel<F, T> {

    protected final FarmContext farm;
    protected final SingleLiveEvent<NavTarget> openPage = new SingleLiveEvent<>();

    protected FinanceListViewModel(@NonNull FarmContext farm, @Nullable F initialFilter) {
        super(initialFilter);
        this.farm = farm;
    }

    public SingleLiveEvent<NavTarget> getOpenPage() {
        return openPage;
    }

    protected boolean denyStaff() {
        if (farm.isStaff()) {
            setUiState(UiState.noPermission(FinanceErrors.MSG_NO_PERMISSION, ErrorCodes.ROLE_FORBIDDEN));
            return true;
        }
        return false;
    }

    /** 分页首屏失败（由页面的 LoadState 回调传入）：40303 切换为无权查看，其余交给页面显示加载失败 */
    public boolean onListError(@NonNull Throwable t) {
        ApiException e = ApiException.from(t);
        if (ErrorCodes.isNoPermission(e.getCode())) {
            setUiState(UiState.noPermission(FinanceErrors.noPermissionText(farm, e), e.getCode()));
            return true;
        }
        return false;
    }

    @Override
    protected void applyErrorState(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.ROLE_FORBIDDEN) {
            setUiState(UiState.noPermission(FinanceErrors.noPermissionText(farm, e), e.getCode()));
            return;
        }
        super.applyErrorState(e);
    }

    @Override
    protected void onNoPermission(@NonNull ApiException e) {
        if (e.getCode() == ErrorCodes.ROLE_FORBIDDEN) {
            setUiState(UiState.noPermission(FinanceErrors.noPermissionText(farm, e), e.getCode()));
            return;
        }
        super.onNoPermission(e);
    }

    protected void open(@NonNull NavTarget target) {
        openPage.setValue(target);
    }

    protected void notifyTodoChanged(@Nullable String bizType, @Nullable Long bizId) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, bizType, bizId, farm.farmIdOrZero()));
        }
    }
}
