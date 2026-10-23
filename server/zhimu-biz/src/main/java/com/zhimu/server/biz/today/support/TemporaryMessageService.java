package com.zhimu.server.biz.today.support;

import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.BizType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 临时实现：只写日志。第 3 批模块03 实现正式的 MessageService 后删除本类 */
@Slf4j
@Service
public class TemporaryMessageService implements MessageService {

    @Override
    public void send(MessageCommand command) {
        log.info("[临时] 站内消息 type={} farm={} to={} title={} biz={}:{} route={}", command.getMsgType(),
                command.getFarmId(), command.getReceiverUserIds(), command.getTitle(), command.getBizType(),
                command.getBizId(), command.getRoute());
    }

    @Override
    public void hideByBiz(BizType bizType, Long bizId) {
        log.info("[临时] 隐藏消息 biz={}:{}", bizType, bizId);
    }

    @Override
    public void hideFarmMessagesForUser(Long farmId, Long userId) {
        log.info("[临时] 隐藏成员消息 farm={} user={}", farmId, userId);
    }
}