package com.zhimu.server.admin.system.query;

import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * ADM-S30 操作日志列表查询：date_from、date_to 默认近 7 天含今天，跨度 ≤366 天；
 * admin_id 按操作人筛选；op_type 类型筛选；keyword 匹配对象、检索词与原因。
 */
@Getter
@Setter
public class OperationLogQuery extends PageQuery {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long adminId;
    private String opType;

    @Size(max = 30, message = "搜索关键词不能超过 30 个字")
    private String keyword;
}
