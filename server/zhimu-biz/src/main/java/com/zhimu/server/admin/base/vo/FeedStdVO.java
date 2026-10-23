package com.zhimu.server.admin.base.vo;

import lombok.Data;

/** 饲喂标准（ADM-D07 std_type = feed）；没有版本时不输出 online / draft。 */
@Data
public class FeedStdVO {

    private Long breedId;
    private FeedStdVersionVO online;
    private FeedStdVersionVO draft;
}
