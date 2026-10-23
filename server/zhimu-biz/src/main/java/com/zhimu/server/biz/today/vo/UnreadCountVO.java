package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** 未读数（API-T13）：total_unread 用于一级页角标，filtered_unread 用于“未读 3”。 */
@Getter
@Setter
public class UnreadCountVO {

    private Integer totalUnread;
    private Integer filteredUnread;
    /** 有未读的分类（只返回非 0 的键） */
    private Map<String, Integer> byCategory;
}
