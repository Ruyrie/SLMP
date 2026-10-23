package com.zhimu.server.biz.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.OperationLog;
import com.zhimu.server.biz.common.query.RecordLogRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作留痕（t_operation_log）。日志只追加；修改记录查询在 XML 中左连 t_change_request 取申请编号。
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /** 某条记录的用户端修改记录（operator_type = USER），按时间倒序，最多 limit 条 */
    List<RecordLogRow> selectUserLogs(@Param("bizType") String bizType,
                                      @Param("bizId") Long bizId,
                                      @Param("limit") int limit);
}
