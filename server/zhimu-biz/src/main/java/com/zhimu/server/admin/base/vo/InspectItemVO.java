package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 巡检项目与可选结果。 */
@Getter
@Setter
public class InspectItemVO {

    private Long itemId;
    private Integer sortNo;
    private String itemName;
    private String checkNote;
    private Boolean enabled;
    private String disableReason;
    /** ITEM_CHANGE */
    private String changeType;
    private List<InspectOptionVO> options = new ArrayList<>();
    /** 仅已修改项返回线上值（供对比） */
    private InspectItemVO before;
}
