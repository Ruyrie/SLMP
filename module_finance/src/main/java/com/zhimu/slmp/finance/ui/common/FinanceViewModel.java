package com.zhimu.slmp.finance.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.service.ITodoService;
import com.zhimu.slmp.common.service.ServiceHub;
import com.zhimu.slmp.common.service.TodoChange;
import com.zhimu.slmp.finance.util.FinanceErrors;
import com.zhimu.slmp.finance.util.NavTarget;

/**
 * 模块10 ViewModel 基类：员工直接显示 U03 无权查看（技术准备文档 1.4）；40303 按 FarmContext 选文案；
 * 页面跳转事件 openPage；登记成功后通知待办刷新（ITodoService 为空时跳过）。
 */
public abstract class FinanceViewModel extends BaseViewModel {

    protected final FarmContext farm;
    protected final SingleLiveEvent<NavTarget> openPage = new SingleLiveEvent<>();

    protected FinanceViewModel(@NonNull FarmContext farm) {
        this.farm = farm;
    }

    public SingleLiveEvent<NavTarget> getOpenPage() {
        return openPage;
    }

    /** 员工：整页无权查看并返回 true */
    protected boolean denyStaff() {
        if (farm.isStaff()) {
            setUiState(UiState.noPermission(FinanceErrors.MSG_NO_PERMISSION, ErrorCodes.ROLE_FORBIDDEN));
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

    /** 记账、出栏、收款成功后通知首页与日历刷新经营类待办 */
    protected void notifyTodoChanged(@Nullable String bizType, @Nullable Long bizId) {
        ITodoService todo = ServiceHub.get(ITodoService.class);
        if (todo != null) {
            todo.notifyTodoChanged(new TodoChange(null, bizType, bizId, farm.farmIdOrZero()));
        }
    }
}
