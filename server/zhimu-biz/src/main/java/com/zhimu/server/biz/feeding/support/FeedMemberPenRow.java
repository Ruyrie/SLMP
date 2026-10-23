package com.zhimu.server.biz.feeding.support;

import lombok.Data;

/** 模块02 员工负责栏舍只读行。 */
@Data
public class FeedMemberPenRow {
    private Long memberId;
    private Long userId;
    private Long penId;
}
