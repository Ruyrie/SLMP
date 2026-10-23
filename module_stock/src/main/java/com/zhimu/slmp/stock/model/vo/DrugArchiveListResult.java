package com.zhimu.slmp.stock.model.vo;

import java.util.List;

/** API-M34 响应包装（服务端未命名，待定事项 1）；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugArchiveListResult {
    public int total;
    public List<DrugArchiveItemVO> list;
}
