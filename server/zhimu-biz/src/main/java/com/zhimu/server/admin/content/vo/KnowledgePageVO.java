package com.zhimu.server.admin.content.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

/** 知识条目列表：额外返回“补充任务”标签页角标。 */
@Getter
@Setter
public class KnowledgePageVO extends PageVO<KnowledgeRowVO> {

    private Integer taskPendingCount;

    public KnowledgePageVO(java.util.List<KnowledgeRowVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}
