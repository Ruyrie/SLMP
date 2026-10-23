package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I02 某日栏舍清单查询。status：ALL / PENDING / ABNORMAL / DONE。 */
@Data
public class InspectionDayQuery {

    private String date;
    private String status;
    private Integer page;
    private Integer size;
}
