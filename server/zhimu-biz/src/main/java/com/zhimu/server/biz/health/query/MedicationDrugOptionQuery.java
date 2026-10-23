package com.zhimu.server.biz.health.query;

import lombok.Getter;
import lombok.Setter;

/** API-D20 可选兽药查询：batch_id 或 pen_id 必填其一。 */
@Getter
@Setter
public class MedicationDrugOptionQuery {

    private Long batchId;
    private Long penId;
    private String keyword;
}
