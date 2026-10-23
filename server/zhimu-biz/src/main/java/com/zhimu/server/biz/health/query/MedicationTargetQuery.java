package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** API-D19 用药对象下拉查询：target_type 必填（BATCH / PEN），keyword 选填。 */
@Getter
@Setter
public class MedicationTargetQuery {

    private String targetType;
    private String keyword;
}
