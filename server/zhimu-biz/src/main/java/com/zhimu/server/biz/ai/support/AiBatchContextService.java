package com.zhimu.server.biz.ai.support;

import com.zhimu.server.biz.ai.vo.LinkedBatchVO;

import java.util.List;

/**
 * AI 批次上下文：读取真实在栏批次、负责栏舍与最新称重，供关联面板和每轮提问使用。
 */
public interface AiBatchContextService {

    /** 可关联的在栏批次：员工只返回负责栏舍的批次 */
    List<LinkedBatchVO> listLinkable(Long farmId, boolean staff, Iterable<Long> penIds);

    /** 按 ID 取在栏批次；不存在、不属于该场或员工无负责栏舍时返回 null */
    LinkedBatchVO resolve(Long farmId, Long batchId, boolean staff, Iterable<Long> penIds);

    /** 从会话快照 JSON 反序列化 */
    LinkedBatchVO fromSnapshot(String snapshotJson);

    /** 序列化为快照 JSON */
    String toSnapshot(LinkedBatchVO batch);
}
