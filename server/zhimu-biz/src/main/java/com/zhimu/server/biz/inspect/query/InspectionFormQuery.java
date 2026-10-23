package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I06 表单上下文查询。mode：REGISTER / MODIFY / BACKFILL。 */
@Data
public class InspectionFormQuery {

    private Long penId;
    private String mode;
    private String inspectDate;
    private Long inspectionId;
}
