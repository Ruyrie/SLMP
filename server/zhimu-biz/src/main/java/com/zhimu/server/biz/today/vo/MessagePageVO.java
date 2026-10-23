package com.zhimu.server.biz.today.vo;

import com.zhimu.server.common.api.PageVO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 消息列表（API-T12 data）：PageVO + filtered_unread。 */
@Getter
@Setter
public class MessagePageVO extends PageVO<MessageBriefVO> {

    private Integer filteredUnread;

    public MessagePageVO() {
    }

    public MessagePageVO(List<MessageBriefVO> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}
