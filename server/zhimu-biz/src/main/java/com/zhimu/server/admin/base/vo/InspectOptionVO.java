package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

/** 巡检结果选项。 */
@Getter
@Setter
public class InspectOptionVO {

    private Long optionId;
    private String optionName;
    private Boolean isAbnormal;

    public InspectOptionVO() {
    }

    public InspectOptionVO(Long optionId, String optionName, Boolean isAbnormal) {
        this.optionId = optionId;
        this.optionName = optionName;
        this.isAbnormal = isAbnormal;
    }
}
