package com.zhimu.server.biz.stock.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-M27 选择批号（App {@code DrugLotOptionsVO}）。 */
@Data
public class DrugLotOptionsVO {

    private List<DrugLotOptionVO> selectable = new ArrayList<>();
    private List<DrugLotOptionVO> unselectable = new ArrayList<>();
    private Long defaultLotId;
}
