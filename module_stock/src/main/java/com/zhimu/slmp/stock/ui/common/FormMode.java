package com.zhimu.slmp.stock.ui.common;

/** 单据页面状态：NEW 登记、VIEW 只读、EDIT 修改（技术准备文档 2.4.4 的 mode）。 */
public final class FormMode {

    public static final String NEW = "NEW";
    public static final String VIEW = "VIEW";
    public static final String EDIT = "EDIT";

    private FormMode() {
    }
}
