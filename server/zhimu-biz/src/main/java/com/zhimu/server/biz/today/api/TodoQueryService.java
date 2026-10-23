package com.zhimu.server.biz.today.api;

/** 待办查询（模块03 实现，第 3 批）；在此之前返回 0 */
public interface TodoQueryService {

    /** 成员在该养殖场待处理的待办数（移出成员影响预览用） */
    int countPendingForMember(Long farmId, Long userId);
}