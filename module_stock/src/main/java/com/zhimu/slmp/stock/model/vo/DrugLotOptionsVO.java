package com.zhimu.slmp.stock.model.vo;

import java.util.List;

/** API-M27 选择批号（M11）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugLotOptionsVO {
    public List<DrugLotOptionVO> selectable;
    public List<DrugLotOptionVO> unselectable;
    public Long defaultLotId;
}
