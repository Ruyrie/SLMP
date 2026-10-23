package com.zhimu.server.biz.today.vo;

import lombok.Getter;
import lombok.Setter;

/** 待办详情中的关联批次。 */
@Getter
@Setter
public class RelatedBatchVO {

    private Long batchId;
    private String batchNo;
    private String penName;
    private String desc;
    private String tagText;
    private LinkVO link;
}
