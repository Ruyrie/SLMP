package com.zhimu.server.biz.farm.vo;

import lombok.Data;

import java.util.List;

/** 当前养殖场上下文（F02、F03，App 的 FarmContextVO） */
@Data
public class FarmContextVO {
    private Long farmId;
    private String name;
    /** PENDING / REJECTED / NORMAL / DISABLED / FROZEN */
    private String status;
    private String statusText;
    private String speciesText;
    private String mainCategoryName;
    private Long mainCategoryId;
    /** HEAD / BIRD */
    private String countUnit;
    /** OWNER / MANAGER / STAFF */
    private String role;
    private String roleText;
    private String restrictTip;
    /** 只对员工输出 */
    private List<Long> myPenIds;
    private FarmPermissionVO permissions;
}
