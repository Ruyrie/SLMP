package com.zhimu.server.biz.herd.api;

/**
 * 栏舍简要信息（字段名以模块04 为准：pen_code、pen_name、pen_type、capacity）。
 *
 * @param status BASE_STATUS：ENABLED / DISABLED
 */
public record PenInfo(Long penId, Long farmId, String penCode, String penName, String penType, String penTypeText,
                      Integer capacity, String status) {

    public boolean enabled() {
        return "ENABLED".equals(status);
    }
}