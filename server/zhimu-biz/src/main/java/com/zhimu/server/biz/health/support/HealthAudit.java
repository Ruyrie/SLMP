package com.zhimu.server.biz.health.support;

import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.framework.oplog.OperationLogEntry;
import com.zhimu.server.framework.oplog.OperationLogRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模块08 的修改记录（t_operation_log）：op_type 固定 RECORD_EDIT，op_action 取 2.9 节的口径。
 * 与业务同一事务提交（直接注入记录器，不只依赖切面）。
 */
@Component
@RequiredArgsConstructor
public class HealthAudit {

    private final OperationLogRecorder recorder;

    public void record(Long farmId, String bizType, Long bizId, String opAction, String targetName,
                       String searchText, Object before, Object after) {
        recorder.record(OperationLogEntry.builder()
                .operatorType("USER")
                .operatorId(UserContext.userId())
                .opType("RECORD_EDIT")
                .opAction(opAction)
                .bizType(bizType)
                .bizId(bizId)
                .farmId(farmId)
                .targetName(targetName)
                .searchText(searchText)
                .before(before)
                .after(after)
                .build());
    }
}
