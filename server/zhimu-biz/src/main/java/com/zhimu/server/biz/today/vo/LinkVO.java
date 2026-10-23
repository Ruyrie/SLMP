package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** 待办与消息的去向：App 页面编号 + 参数；need_switch 为真时客户端先切换养殖场。 */
@Getter
@Setter
public class LinkVO {

    private String route;
    private String webRoute;
    private Long farmId;
    private String farmName;
    private Boolean needSwitch;
    private Map<String, String> params;
    private String pageTitle;
}
