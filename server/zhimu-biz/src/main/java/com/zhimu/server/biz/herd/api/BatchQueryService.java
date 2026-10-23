package com.zhimu.server.biz.herd.api;

import java.util.List;

/**
 * 批次查询（模块04 实现，模块05–10 使用）。只读，不校验当前养殖场上下文；调用方自行判断返回的 farmId。
 */
public interface BatchQueryService {

    /** 按 ID 取批次；不存在或已删除抛 40401 */
    BatchInfo requireBatch(Long batchId);

    /** 养殖场全部在养批次（status = ACTIVE），按入栏日期升序、批次 ID 升序 */
    List<BatchInfo> listActive(Long farmId);

    /** 批次存在、未删除且在养 */
    boolean isActive(Long batchId);
}
