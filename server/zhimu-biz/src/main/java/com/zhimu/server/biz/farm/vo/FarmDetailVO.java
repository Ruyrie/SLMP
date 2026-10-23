package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** 养殖场信息（F09、F11、F12） */
@Data
public class FarmDetailVO {
    private Long farmId;
    private Integer currentStock;
    private String name;
    private String status;
    private String statusText;
    private String role;
    private String roleText;
    private String speciesText;
    private String mainCategoryName;
    private String countUnit;
    private String approvedAt;
    private String disabledAt;
    private String restrictTip;
    private FarmBasicVO basic;
    private RegistrationVO registration;
    private MemberSummaryVO memberSummary;
    private FarmPermissionVO permissions;
    private ChangeBriefVO pendingChange;
    private ChangeBriefVO rejectedChange;
    /** 只对所有者输出 */
    private TransferVO pendingTransfer;
}
