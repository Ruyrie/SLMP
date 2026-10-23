package com.zhimu.server.biz.health.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-D20 返回：列表 + 关键词命中禁用药时的提示。 */
@Data
public class MedicationDrugOptionsVO {

    private List<MedicationDrugOptionVO> list = new ArrayList<>();
    private String bannedHint;
}
