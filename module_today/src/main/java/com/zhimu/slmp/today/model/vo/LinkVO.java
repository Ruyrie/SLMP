package com.zhimu.slmp.today.model.vo;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * 待办与消息的去向：App 页面编号 route 与参数；need_switch 为真时先切换养殖场（API-F03）。
 */
public class LinkVO {

    /** App 页面编号，例如 G02 */
    @Nullable
    private String route;
    @Nullable
    private String webRoute;
    private long farmId;
    @Nullable
    private String farmName;
    private boolean needSwitch;
    /** Gson 把数字读为字符串 */
    @Nullable
    private Map<String, String> params;
    @Nullable
    private String pageTitle;

    @Nullable
    public String getRoute() {
        return route;
    }

    @Nullable
    public String getWebRoute() {
        return webRoute;
    }

    public long getFarmId() {
        return farmId;
    }

    @Nullable
    public String getFarmName() {
        return farmName;
    }

    public boolean isNeedSwitch() {
        return needSwitch;
    }

    @Nullable
    public Map<String, String> getParams() {
        return params;
    }

    @Nullable
    public String getPageTitle() {
        return pageTitle;
    }
}
