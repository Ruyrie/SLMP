package com.zhimu.server.biz.today.service;

import com.zhimu.server.biz.today.query.MessageQuery;
import com.zhimu.server.biz.today.query.UnreadCountQuery;
import com.zhimu.server.biz.today.request.MessageOpenRequest;
import com.zhimu.server.biz.today.request.MessageReadAllRequest;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDeleteResult;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.MessagePageVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.biz.today.vo.ReadAllResult;
import com.zhimu.server.biz.today.vo.UnreadCountVO;

/** 消息中心（API-T12–T18），只按当前用户。 */
public interface MessageQueryService {

    MessagePageVO list(MessageQuery query, Long userId);

    UnreadCountVO unreadCount(UnreadCountQuery query, Long userId);

    ReadAllResult readAll(MessageReadAllRequest request, Long userId);

    OpenResultVO open(Long messageId, MessageOpenRequest request, Long userId);

    MessageDetailVO detail(Long messageId, Long userId);

    MessageDeleteResult delete(Long messageId, Long userId);

    MessageBriefVO restore(Long messageId, Long userId);
}
