package com.zhimu.server.biz.stock.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** API-M34 响应包装（App {@code DrugArchiveListResult}）：不分页，前端按类别分组。 */
@Data
public class DrugArchiveListResult {

    private int total;
    private List<DrugArchiveItemVO> list = new ArrayList<>();
}
