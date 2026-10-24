package com.zhimu.server.biz.inspect.query;

import lombok.Data;

/** API-I27 异常上报列表查询：按养殖场、状态、栏舍、批次、紧急程度、关键字与日期范围分页。 */
@Data
public class AbnormalReportListQuery {

    private Integer page;
    private Integer size;
    /** ALL / PENDING / DIAGNOSED / HANDLED / CLOSED */
    private String status;
    private Long penId;
    private Long batchId;
    /** ALL / NORMAL / ATTENTION / URGENT */
    private String urgency;
    private String keyword;
    /** yyyy-MM-dd，含当天 */
    private String startDate;
    private String endDate;
    /** 只看我上报的 */
    private Boolean mine;
}
