package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I14 上报表单上下文查询：inspection_id 或 pen_id 二选一。 */
@Data
public class ReportFormQuery {

    private Long inspectionId;
    private Long penId;
}
