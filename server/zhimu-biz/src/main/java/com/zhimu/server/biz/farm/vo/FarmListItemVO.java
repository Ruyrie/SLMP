package com.zhimu.server.biz.farm.vo;

import lombok.Data;

/** F01 列表项（App 的 FarmListItemVO；员工行不输出品类与存栏，只有 my_pen_count） */
@Data
public class FarmListItemVO {
    private Long farmId;
    private String name;
    private String status;
    private String statusText;
    private String role;
    private String roleText;
    /** CREATE / INVITE */
    private String joinType;
    private String speciesText;
    private String mainCategoryName;
    private String countUnit;
    private String countUnitText;
    /** 所有者、场长且 NORMAL / DISABLED */
    private Integer currentStock;
    /** PENDING / REJECTED */
    private Integer scale;
    /** 员工行 */
    private Integer myPenCount;
    private Boolean isCurrent;
    private Boolean canSwitch;
    private Boolean canQuit;
    private String lastSwitchAt;
}
