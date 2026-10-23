package com.zhimu.server.biz.health.vo;

import lombok.Data;

/** 简单 toast 返回（D09 删除、D26 删除）。 */
@Data
public class ToastVO {

    private String toast;

    public static ToastVO of(String toast) {
        ToastVO vo = new ToastVO();
        vo.setToast(toast);
        return vo;
    }
}
