package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeRecipient;
import com.zhimu.server.admin.content.entity.NoticeTarget;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeRecipientMapper;
import com.zhimu.server.admin.content.mapper.NoticeTargetMapper;
import com.zhimu.server.admin.content.support.NoticeRichText;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageRef;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通知分发（ADM-C04/C05 立即发送、NoticeSendJob 定时发送与断点续发共用）：
 * 解析接收人 → 分批调用 MessageService 写 t_message（每个账号一条，msg_type = NOTICE）→
 * 写 t_notice_recipient → 回写 delivered_count 与 dispatch_status。
 * 已存在的消息不再重发，唯一键保证续发不重复送达。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeDispatchService {

    private static final int BATCH_SIZE = 500;

    private final NoticeMapper noticeMapper;
    private final NoticeTargetMapper targetMapper;
    private final NoticeRecipientMapper recipientMapper;
    private final NoticeAudienceService audienceService;
    private final MessageService messageService;

    @Transactional
    public void dispatch(Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null || !"SENT".equals(notice.getStatus())) {
            return;
        }
        if ("DONE".equals(notice.getDispatchStatus())) {
            return;
        }
        int running = noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .set(Notice::getDispatchStatus, "RUNNING")
                .eq(Notice::getId, noticeId)
                .eq(Notice::getStatus, "SENT"));
        if (running == 0) {
            return;
        }
        List<NoticeTarget> targets = targetMapper.selectList(Wrappers.<NoticeTarget>lambdaQuery()
                .eq(NoticeTarget::getNoticeId, noticeId));
        NoticeAudienceService.Result audience;
        try {
            audience = audienceService.resolveForNotice(notice, targets);
        } catch (RuntimeException e) {
            log.warn("通知 {} 接收人解析失败：{}", noticeId, e.getMessage());
            audience = new NoticeAudienceService.Result(List.of(), 0);
        }

        Map<Long, MessageRef> existing = new LinkedHashMap<>();
        for (MessageRef ref : messageService.listByBiz(BizType.NOTICE, noticeId)) {
            existing.putIfAbsent(ref.userId(), ref);
        }
        List<NoticeAudienceService.Recipient> pending = new ArrayList<>();
        for (NoticeAudienceService.Recipient recipient : audience.recipients()) {
            if (!existing.containsKey(recipient.userId())) {
                pending.add(recipient);
            }
        }
        for (int from = 0; from < pending.size(); from += BATCH_SIZE) {
            List<NoticeAudienceService.Recipient> batch = pending.subList(from, Math.min(from + BATCH_SIZE, pending.size()));
            sendBatch(notice, batch);
        }

        Map<Long, MessageRef> sent = new LinkedHashMap<>();
        for (MessageRef ref : messageService.listByBiz(BizType.NOTICE, noticeId)) {
            sent.putIfAbsent(ref.userId(), ref);
        }
        List<NoticeRecipient> rows = new ArrayList<>();
        LocalDateTime now = BeijingTime.now();
        for (NoticeAudienceService.Recipient recipient : audience.recipients()) {
            MessageRef ref = sent.get(recipient.userId());
            if (ref == null) {
                continue;
            }
            NoticeRecipient row = new NoticeRecipient();
            row.setNoticeId(noticeId);
            row.setUserId(recipient.userId());
            row.setRoleSnapshot(recipient.role() == null ? "NONE" : recipient.role());
            row.setFarmId(recipient.farmId());
            row.setMessageId(ref.messageId());
            row.setIsHidden(false);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            recipientMapper.insertIgnore(rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
        }
        long delivered = recipientMapper.selectCount(Wrappers.<NoticeRecipient>lambdaQuery()
                .eq(NoticeRecipient::getNoticeId, noticeId));
        noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .set(Notice::getDeliveredCount, (int) delivered)
                .set(Notice::getDispatchStatus, "DONE")
                .eq(Notice::getId, noticeId)
                .eq(Notice::getStatus, "SENT"));
        log.info("通知 {} 分发完成，送达 {} 个账号", noticeId, delivered);
    }

    /** 补齐 dispatch_status 为 PENDING/RUNNING 的通知（定时任务续发） */
    public void resumePending() {
        List<Notice> pending = noticeMapper.selectList(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, "SENT")
                .in(Notice::getDispatchStatus, "PENDING", "RUNNING")
                .lt(Notice::getSentAt, BeijingTime.now().minusMinutes(5))
                .last("LIMIT 20"));
        for (Notice notice : pending) {
            try {
                dispatch(notice.getId());
            } catch (RuntimeException e) {
                log.warn("通知 {} 续发失败：{}", notice.getId(), e.getMessage());
            }
        }
    }

    private void sendBatch(Notice notice, List<NoticeAudienceService.Recipient> batch) {
        Map<Long, Set<Long>> byFarm = new LinkedHashMap<>();
        for (NoticeAudienceService.Recipient recipient : batch) {
            byFarm.computeIfAbsent(recipient.farmId(), key -> new LinkedHashSet<>()).add(recipient.userId());
        }
        String summary = NoticeRichText.summary(notice.getContent(), 60);
        MsgLevel level = "IMPORTANT".equals(notice.getLevel()) ? MsgLevel.IMPORTANT : MsgLevel.NORMAL;
        for (Map.Entry<Long, Set<Long>> entry : byFarm.entrySet()) {
            MessageCommand command = MessageCommand.builder()
                    .msgType(MsgType.NOTICE)
                    .level(level)
                    .farmId(entry.getKey())
                    .receiverUserIds(entry.getValue())
                    .title(notice.getTitle())
                    .summary(summary)
                    .bizType(BizType.NOTICE)
                    .bizId(notice.getId())
                    .mergeKey("NOTICE:" + notice.getId())
                    .route("T07")
                    .extra("notice_id", notice.getId())
                    .extra("notice_level", notice.getLevel())
                    .build();
            messageService.send(command);
        }
    }
}
