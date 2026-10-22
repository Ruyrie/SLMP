package com.zhimu.slmp.today.model.vo;

import com.zhimu.slmp.network.model.PageVO;

/**
 * 消息列表（API-T12 data；客户端暂定类名）：PageVO + filtered_unread。
 */
public class MessagePageVO extends PageVO<MessageBriefVO> {

    private int filteredUnread;

    public int getFilteredUnread() {
        return filteredUnread;
    }
}
