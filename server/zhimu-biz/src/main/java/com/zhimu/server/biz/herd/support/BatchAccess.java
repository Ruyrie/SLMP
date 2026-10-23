package com.zhimu.server.biz.herd.support;

import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 批次访问：当前养殖场内存在、未删除、员工可见，否则 40401 / 41001 / 40304。 */
@Component
@RequiredArgsConstructor
public class BatchAccess {

    private final BatchMapper batchMapper;
    private final HerdSupport support;

    /** 当前养殖场的批次；已删除返回 41001，不存在或不属于本场返回 40401，员工越权返回 40304 */
    public Batch require(Long batchId) {
        FarmContext.FarmMember member = FarmContext.require();
        Batch batch = batchId == null ? null : TenantSkip.call(() -> batchMapper.selectByIdIncludingDeleted(batchId));
        if (batch == null || !member.farmId().equals(batch.getFarmId())) {
            throw HerdErrors.notFound();
        }
        if (Boolean.TRUE.equals(batch.getDeleted())) {
            throw HerdErrors.deletedBatch();
        }
        if (!support.visibleToStaff(batch, member)) {
            throw HerdErrors.batchForbidden();
        }
        return batch;
    }

    /** 不判断员工可见范围（写操作内部使用；入口已先调 require） */
    public Batch requireInFarm(Long batchId) {
        FarmContext.FarmMember member = FarmContext.require();
        Batch batch = batchId == null ? null : batchMapper.selectById(batchId);
        if (batch == null || !member.farmId().equals(batch.getFarmId())) {
            throw HerdErrors.notFound();
        }
        return batch;
    }

    /** 员工登记时需要负责该栏舍；所有者、场长直接通过 */
    public void requireOperatePen(Long penId) {
        FarmContext.FarmMember member = FarmContext.require();
        if (member.isStaff() && (penId == null || member.penIds() == null || !member.penIds().contains(penId))) {
            throw HerdErrors.penForbidden();
        }
    }
}
