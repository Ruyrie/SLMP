package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 全部已读结果（API-T14）。 */
@Getter
@Setter
public class ReadAllResult {

    private Integer updatedCount;
    private Integer totalUnread;
}
