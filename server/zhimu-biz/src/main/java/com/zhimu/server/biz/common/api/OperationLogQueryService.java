package com.zhimu.server.biz.common.api;

import com.zhimu.server.biz.common.vo.RecordLogVO;
import com.zhimu.server.common.enums.BizType;

import java.util.List;

/**
 * 记录的修改记录查询（API-U14 用，公共能力 biz.common 实现）。读 t_operation_log 中 operator_type = USER 的日志。
 */
public interface OperationLogQueryService {

    /**
     * 某条记录的用户端修改记录，按时间倒序，最多 limit 条（上限 200）。
     *
     * @return 没有记录时返回空列表，不返回 null
     */
    List<RecordLogVO> listByBiz(BizType bizType, Long bizId, int limit);
}
