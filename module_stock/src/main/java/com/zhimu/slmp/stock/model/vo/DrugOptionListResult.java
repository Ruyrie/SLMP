package com.zhimu.slmp.stock.model.vo;

import java.util.List;

/** API-M35 响应包装（服务端未命名，待定事项 1）；banned_hint 搜索词命中禁用药时的说明；字段直接公开，由 Gson 按下划线命名读写。 */
public class DrugOptionListResult {
    public List<DrugOptionVO> list;
    public String bannedHint;
}
