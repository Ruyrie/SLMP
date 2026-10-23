package com.zhimu.server.biz.stock.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-M35 响应包装（App {@code DrugOptionListResult}）：banned_hint 为搜索词命中禁用药时的说明。 */
@Data
public class DrugOptionListResult {

    private List<DrugOptionVO> list = new ArrayList<>();
    private String bannedHint;
}
