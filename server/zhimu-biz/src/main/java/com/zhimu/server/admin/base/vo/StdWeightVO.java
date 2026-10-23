package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 标准体重（ADM-D07 std_type = weight）；三个版本不存在时不输出该键。 */
@Data
public class StdWeightVO {

    private Long breedId;
    private StdWeightVersionVO online;
    private StdWeightVersionVO scheduled;
    private StdWeightVersionVO draft;
}
