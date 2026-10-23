package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D35 消毒剂下拉与库存提示。 */
@Data
public class DisinfectantOptionVO {

    private Long catalogId;
    private Long drugId;
    private String name;
    private String recommendedConcentration;
    private List<String> methodCodes = new ArrayList<>();
    /** NORMAL / NEAR_EXPIRY / NO_STOCK */
    private String stockHintType;
    private String stockHintText;
    private String stockUnit;
}
