package com.zhimu.server.biz.today.api;

import java.util.List;

/**
 * 来源模块写入待办的公共能力（2.8.1）：
 * <ul>
 *     <li>同一 (farm_id, todo_type, dedup_key) 只保留一条，重复生成只更新展示字段，不改变状态；</li>
 *     <li>来源记录登记后由来源模块调用 {@link #completeFromSource} 自动完成待办；</li>
 *     <li>超过补登期限等系统关闭场景调用 {@link #ignoreFromSource} 自动忽略；</li>
 *     <li>来源记录作废时调用 {@link #reopenFromSource} 只恢复由该记录登记完成的待办。</li>
 * </ul>
 * 与 {@link TodoSource} 配套使用：来源模块实现生成逻辑（草稿），由本接口统一落库。
 */
public interface TodoSourceWriteService {

    /** 按 (farm_id, todo_type, dedup_key) 幂等生成或更新待办；返回本次涉及的待办 ID */
    List<Long> upsertFromSource(Long farmId, List<TodoSource.TodoDraft> drafts);

    /**
     * 来源记录登记后自动完成待办；只有 PENDING 会流转为 DONE。
     *
     * @param recordType 完成依据记录类型，例如 FEED_RECORD
     * @param recordId   完成依据记录 ID
     * @param operatorId 操作人；系统任务可传 0
     * @return 是否发生状态流转
     */
    boolean completeFromSource(Long farmId, String todoType, String dedupKey, String recordType,
                               Long recordId, Long operatorId);

    /** 系统关闭待办（例如超过补登期限）；只有 PENDING 会流转为 IGNORED，忽略方式为 AUTO */
    boolean ignoreFromSource(Long farmId, String todoType, String dedupKey, String reason);

    /**
     * 撤销来源记录的登记后恢复待办：只恢复由该记录以 REGISTER 方式完成、且当前为 DONE 的待办。
     *
     * @return 是否恢复
     */
    boolean reopenFromSource(Long farmId, String todoType, String dedupKey, Long recordId);

    /** 来源计划恢复时，只恢复被指定系统原因关闭的待办，不恢复用户手工忽略的待办。 */
    boolean reopenAutoIgnoredFromSource(Long farmId, String todoType, String dedupKey, String ignoreReason);
}
