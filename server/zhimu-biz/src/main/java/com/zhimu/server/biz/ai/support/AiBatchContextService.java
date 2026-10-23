package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.LinkedBatchVO;

import java.util.List;

/**
 * 批次上下文（模块04 未接入前的临时实现）：返回与 App Mock 一致的东山猪场在栏批次。
 * 模块04 落地后由 BatchQueryService 替换本接口的实现，调用方不变。
 */
public interface AiBatchContextService {

    /** 可关联的在栏批次：员工只返回负责栏舍的批次 */
    List<LinkedBatchVO> listLinkable(Long farmId, boolean staff, Iterable<Long> penIds);

    /** 按 ID 取批次；不存在或不属于该场返回 null */
    LinkedBatchVO resolve(Long farmId, Long batchId, boolean staff, Iterable<Long> penIds);

    /** 从会话快照 JSON 反序列化 */
    LinkedBatchVO fromSnapshot(String snapshotJson);

    /** 序列化为快照 JSON */
    String toSnapshot(LinkedBatchVO batch);
}
