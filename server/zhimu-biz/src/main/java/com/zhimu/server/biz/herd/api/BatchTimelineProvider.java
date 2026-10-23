package com.zhimu.server.biz.herd.api;

import com.zhimu.server.biz.herd.vo.TimelineItemVO;

import java.util.List;

/**
 * 批次时间线扩展点（模块06–10 实现后注册）：本模块的时间线接口在读取 t_batch_timeline 后，
 * 再合并各实现返回的记录。当前没有实现时只返回模块04 自己的入栏/转栏/死淘记录。
 * 实现方不要返回金额字段（模块04 文档 2.3.5）。
 */
public interface BatchTimelineProvider {

    /** 该批次由本模块提供的额外时间线记录；无法判断时返回空列表 */
    List<TimelineItemVO> extraRecords(Long farmId, Long batchId);
}
