package com.zhimu.slmp.base.mvvm;

/**
 * 可判断“是否为空”的数据（例如 PageVO）：BaseViewModel.load 据此切换 EMPTY 状态。
 */
public interface EmptyAware {

    boolean isEmptyContent();
}
