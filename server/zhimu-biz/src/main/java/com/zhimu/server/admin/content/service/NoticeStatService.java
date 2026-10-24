package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeRecipient;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeRecipientMapper;
import com.zhimu.server.biz.today.api.MessageReadState;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知阅读统计（NoticeStatJob 与详情共用）：从 t_message.read_at / t_push_record.clicked_at
 * 回写送达明细，再汇总 read_count、push_open_count。统计只更新缓存字段，不改 version。
 */
@Service
@RequiredArgsConstructor
public class NoticeStatService {

    private final NoticeMapper noticeMapper;
    private final NoticeRecipientMapper recipientMapper;
    private final MessageService messageService;

    /** 刷新一条通知的阅读与推送点开数据 */
    @Transactional
    public Notice refresh(Notice notice) {
        if (notice == null || !"SENT".equals(notice.getStatus())) {
            return notice;
        }
        List<NoticeRecipient> recipients = recipientMapper.selectList(Wrappers.<NoticeRecipient>lambdaQuery()
                .eq(NoticeRecipient::getNoticeId, notice.getId())
                .and(w -> w.isNull(NoticeRecipient::getReadAt).or().isNull(NoticeRecipient::getPushOpenedAt)));
        List<Long> messageIds = new ArrayList<>();
        for (NoticeRecipient recipient : recipients) {
            if (recipient.getMessageId() != null) {
                messageIds.add(recipient.getMessageId());
            }
        }
        Map<Long, MessageReadState> states = messageService.listReadState(messageIds);
        for (NoticeRecipient recipient : recipients) {
            MessageReadState state = states.get(recipient.getMessageId());
            if (state == null) {
                continue;
            }
            boolean changed = false;
            if (recipient.getReadAt() == null && state.readAt() != null) {
                recipient.setReadAt(state.readAt());
                changed = true;
            }
            if (recipient.getPushOpenedAt() == null && state.pushClickedAt() != null) {
                recipient.setPushOpenedAt(state.pushClickedAt());
                changed = true;
            }
            if (changed) {
                recipientMapper.updateById(recipient);
            }
        }
        long read = recipientMapper.selectCount(Wrappers.<NoticeRecipient>lambdaQuery()
                .eq(NoticeRecipient::getNoticeId, notice.getId())
                .isNotNull(NoticeRecipient::getReadAt));
        long pushOpen = recipientMapper.selectCount(Wrappers.<NoticeRecipient>lambdaQuery()
                .eq(NoticeRecipient::getNoticeId, notice.getId())
                .isNotNull(NoticeRecipient::getPushOpenedAt));
        LocalDateTime now = BeijingTime.now();
        notice.setReadCount((int) read);
        notice.setPushOpenCount((int) pushOpen);
        notice.setStatAt(now);
        noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .set(Notice::getReadCount, notice.getReadCount())
                .set(Notice::getPushOpenCount, notice.getPushOpenCount())
                .set(Notice::getStatAt, now)
                .eq(Notice::getId, notice.getId()));
        return notice;
    }

    /** 详情打开时：统计超过 5 分钟才重新拉取，避免频繁调用消息服务 */
    public Notice refreshIfStale(Notice notice) {
        if (notice == null || !"SENT".equals(notice.getStatus())) {
            return notice;
        }
        LocalDateTime statAt = notice.getStatAt();
        if (statAt == null || statAt.isBefore(BeijingTime.now().minusMinutes(5))) {
            return refresh(notice);
        }
        return notice;
    }
}
