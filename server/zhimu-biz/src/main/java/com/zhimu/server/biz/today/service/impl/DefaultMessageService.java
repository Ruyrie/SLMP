package com.zhimu.server.biz.today.service.impl;

import com.zhimu.server.biz.account.support.AccountPersonalDataGuard;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.api.MessageCommand;
import com.zhimu.server.biz.today.api.MessageReadState;
import com.zhimu.server.biz.today.api.MessageRef;
import com.zhimu.server.biz.today.api.MessageService;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.PushRecord;
import com.zhimu.server.biz.today.entity.ReminderSetting;
import com.zhimu.server.biz.today.mapper.MessageMapper;
import com.zhimu.server.biz.today.mapper.PushRecordMapper;
import com.zhimu.server.biz.today.mapper.ReminderSettingMapper;
import com.zhimu.server.biz.today.support.TodayJson;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.MsgCategory;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import com.zhimu.server.integration.push.PushChannels;
import com.zhimu.server.integration.push.PushClient;
import com.zhimu.server.integration.push.PushMessage;
import com.zhimu.server.integration.push.PushResult;
import com.zhimu.server.integration.push.PushStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息与推送（MessageService，模块02 等调用）：每个接收人一条消息，提交后按别名推送。
 * 没有 Master Secret 时 {@link PushClient} 只写日志并返回 SKIPPED；免打扰内写入 DEFERRED 记录，由后续推送任务补发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMessageService implements MessageService {

    private final AccountPersonalDataGuard personalDataGuard;
    private final MessageMapper messageMapper;
    private final PushRecordMapper pushRecordMapper;
    private final ReminderSettingMapper reminderSettingMapper;
    private final FarmQueryService farmQueryService;
    private final FarmMemberLoader memberLoader;
    private final PushClient pushClient;

    @Override
    @Transactional
    public void send(MessageCommand command) {
        if (command == null || command.getReceiverUserIds() == null || command.getReceiverUserIds().isEmpty()) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        MsgType msgType = command.getMsgType();
        MsgCategory category = msgType == null ? MsgCategory.OTHER : msgType.getCategory();
        for (Long userId : command.getReceiverUserIds().stream()
                .filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            if (!personalDataGuard.lockActive(userId)) {
                continue;
            }
            var member = command.getFarmId() == null ? null : memberLoader.load(command.getFarmId(), userId);
            Message message = new Message();
            message.setUserId(userId);
            message.setFarmId(command.getFarmId());
            message.setReceiverRole(member == null ? null : member.role().name());
            message.setCategory(category.name());
            message.setMsgType(msgType == null ? null : msgType.name());
            message.setLevel(command.getLevel() == null ? MsgLevel.NORMAL.name() : command.getLevel().name());
            message.setTitle(command.getTitle());
            message.setSummary(command.getSummary());
            message.setTagCode(tagCode(command));
            message.setTagText(tagText(message.getTagCode()));
            message.setBizType(command.getBizType() == null ? null : command.getBizType().name());
            message.setBizId(command.getBizId());
            Map<String, Object> extras = command.getExtras() == null ? Map.of() : command.getExtras();
            Long todoId = TodayJson.lng(extras, "todo_id");
            message.setTodoId(todoId);
            message.setRoute(command.getRoute());
            message.setExtraJson(extras.isEmpty() ? null : TodayJson.write(extras));
            message.setMergeKey(command.getMergeKey() != null ? command.getMergeKey()
                    : (todoId == null ? null : "TODO:" + todoId));
            message.setMergeCount(1);
            message.setSortTime(now);
            message.setHidden(false);
            message.setDeleted(false);
            message.setCreatedBy(0L);
            messageMapper.insert(message);
            push(message, userId, member == null ? null : member.farmName(), now);
        }
    }

    @Override
    @Transactional
    public void hideByBiz(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return;
        }
        messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .set(Message::getHidden, true)
                .eq(Message::getBizType, bizType.name())
                .eq(Message::getBizId, bizId));
    }

    @Override
    @Transactional
    public void hideFarmMessagesForUser(Long farmId, Long userId) {
        if (farmId == null || userId == null) {
            return;
        }
        messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .set(Message::getHidden, true)
                .set(Message::getHiddenReason, "MEMBER_REMOVED")
                .eq(Message::getFarmId, farmId)
                .eq(Message::getUserId, userId)
                .ne(Message::getMsgType, MsgType.MEMBER_REMOVED.name()));
    }

    @Override
    @Transactional
    public int hideUnreadByBiz(BizType bizType, Long bizId, String hiddenReason) {
        if (bizType == null || bizId == null) {
            return 0;
        }
        List<Message> unread = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getBizType, bizType.name())
                .eq(Message::getBizId, bizId)
                .isNull(Message::getReadAt));
        if (unread.isEmpty()) {
            return 0;
        }
        int updated = messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .set(Message::getHidden, true)
                .set(Message::getHiddenReason, hiddenReason)
                .eq(Message::getBizType, bizType.name())
                .eq(Message::getBizId, bizId)
                .isNull(Message::getReadAt));
        List<Long> messageIds = unread.stream().map(Message::getId).toList();
        pushRecordMapper.update(null, Wrappers.<PushRecord>lambdaUpdate()
                .set(PushRecord::getStatus, "SKIPPED")
                .set(PushRecord::getFailReason, "通知已撤回，取消尚未发出的推送")
                .in(PushRecord::getMessageId, messageIds)
                .in(PushRecord::getStatus, "WAITING", "DEFERRED"));
        return updated;
    }

    @Override
    public List<MessageRef> listByBiz(BizType bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return List.of();
        }
        List<Message> messages = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getBizType, bizType.name())
                .eq(Message::getBizId, bizId)
                .orderByAsc(Message::getId));
        List<MessageRef> refs = new ArrayList<>(messages.size());
        for (Message message : messages) {
            refs.add(new MessageRef(message.getId(), message.getUserId(), message.getFarmId(),
                    message.getReadAt()));
        }
        return refs;
    }

    @Override
    public Map<Long, MessageReadState> listReadState(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = messageIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, LocalDateTime> readAt = new HashMap<>();
        Map<Long, LocalDateTime> clickedAt = new HashMap<>();
        for (int from = 0; from < ids.size(); from += 1000) {
            List<Long> chunk = ids.subList(from, Math.min(from + 1000, ids.size()));
            for (Message message : messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                    .in(Message::getId, chunk))) {
                if (message.getReadAt() != null) {
                    readAt.put(message.getId(), message.getReadAt());
                }
            }
            for (PushRecord record : pushRecordMapper.selectList(Wrappers.<PushRecord>lambdaQuery()
                    .in(PushRecord::getMessageId, chunk)
                    .isNotNull(PushRecord::getClickedAt)
                    .orderByAsc(PushRecord::getId))) {
                clickedAt.putIfAbsent(record.getMessageId(), record.getClickedAt());
            }
        }
        Map<Long, MessageReadState> result = new HashMap<>();
        for (Long id : ids) {
            LocalDateTime read = readAt.get(id);
            LocalDateTime clicked = clickedAt.get(id);
            if (read != null || clicked != null) {
                result.put(id, new MessageReadState(read, clicked));
            }
        }
        return result;
    }

    // ===================== 推送 =====================

    private void push(Message message, Long userId, String farmName, LocalDateTime now) {
        boolean todoRemind = MsgCategory.TODO.name().equals(message.getCategory());
        ReminderSetting setting = setting(userId);
        boolean bypass = MsgLevel.IMPORTANT.name().equals(message.getLevel())
                || MsgLevel.URGENT.name().equals(message.getLevel());
        if (todoRemind && setting != null && Boolean.FALSE.equals(setting.getTodoRemindEnabled())) {
            record(message, userId, farmName, now, "SKIPPED", now, "待办提醒已关闭", null, bypass);
            return;
        }
        LocalDateTime planSendAt = quietDelay(setting, now, bypass);
        if (planSendAt != null) {
            record(message, userId, farmName, now, "DEFERRED", planSendAt, "免打扰时段，延后发送", null, bypass);
            return;
        }
        PushRecord record = record(message, userId, farmName, now, "WAITING", now, null, null, bypass);
        String channel = channel(message, bypass);
        PushMessage push = PushMessage.builder()
                .title(pushTitle(farmName, message.getTitle()))
                .content(pushContent(message))
                .channel(channel)
                .extra("route", message.getRoute())
                .extra("farm_id", message.getFarmId())
                .extra("biz_type", message.getBizType())
                .extra("biz_id", message.getBizId())
                .extra("message_id", message.getId())
                .extra("push_id", record.getId())
                .extra("todo_id", message.getTodoId())
                .extra("channel", channel)
                .build();
        PushResult result = pushClient.pushToUsers(java.util.List.of(userId), push);
        record.setStatus(result.status().name());
        record.setSentAt(result.status() == PushStatus.SENT ? now : null);
        record.setJpushMsgId(result.msgIds().isEmpty() ? null : result.msgIds().getFirst());
        record.setFailReason(result.reason());
        pushRecordMapper.updateById(record);
    }

    private PushRecord record(Message message, Long userId, String farmName, LocalDateTime now, String status,
                              LocalDateTime planSendAt, String reason, String msgId, boolean bypass) {
        PushRecord record = new PushRecord();
        record.setUserId(userId);
        record.setFarmId(message.getFarmId());
        record.setMessageId(message.getId());
        record.setTodoId(message.getTodoId());
        record.setPushType(MsgCategory.TODO.name().equals(message.getCategory()) ? "TODO_REMIND" : "MESSAGE");
        record.setChannel("JPUSH");
        record.setNotifyChannel(channel(message, bypass));
        record.setTitle(pushTitle(farmName, message.getTitle()));
        record.setContent(pushContent(message));
        record.setRoute(message.getRoute() == null ? "T01" : message.getRoute());
        record.setBizType(message.getBizType());
        record.setBizId(message.getBizId());
        record.setBypassQuiet(bypass);
        record.setPlanSendAt(planSendAt);
        record.setStatus(status);
        record.setRetryCount(0);
        record.setFailReason(reason);
        record.setJpushMsgId(msgId);
        pushRecordMapper.insert(record);
        return record;
    }

    private ReminderSetting setting(Long userId) {
        return reminderSettingMapper.selectOne(Wrappers.<ReminderSetting>lambdaQuery()
                .eq(ReminderSetting::getUserId, userId)
                .last("LIMIT 1"));
    }

    /** 免打扰内返回结束时刻，否则 null（结束早于开始表示次日） */
    private static LocalDateTime quietDelay(ReminderSetting setting, LocalDateTime now, boolean bypass) {
        if (bypass || setting == null || !Boolean.TRUE.equals(setting.getQuietEnabled())
                || setting.getQuietStart() == null || setting.getQuietEnd() == null) {
            return null;
        }
        LocalTime start = setting.getQuietStart();
        LocalTime end = setting.getQuietEnd();
        LocalTime time = now.toLocalTime();
        if (start.equals(end)) {
            return null;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end) ? now.toLocalDate().atTime(end) : null;
        }
        if (!time.isBefore(start)) {
            return now.toLocalDate().plusDays(1).atTime(end);
        }
        return time.isBefore(end) ? now.toLocalDate().atTime(end) : null;
    }

    private static String channel(Message message, boolean bypass) {
        if (bypass) {
            return PushChannels.URGENT;
        }
        return MsgCategory.TODO.name().equals(message.getCategory()) ? PushChannels.TODO : PushChannels.SYSTEM;
    }

    private static String pushTitle(String farmName, String title) {
        if (title == null) {
            return farmName;
        }
        if (farmName == null || title.startsWith(farmName)) {
            return title;
        }
        return farmName + " · " + title;
    }

    private static String pushContent(Message message) {
        String content = message.getSummary() == null ? message.getTitle() : message.getSummary();
        return content;
    }

    private static String tagCode(MessageCommand command) {
        if (command.getMsgType() == MsgType.LOT_RECALLED) {
            return "RECALLED";
        }
        if (command.getLevel() == MsgLevel.IMPORTANT) {
            return "IMPORTANT";
        }
        if (command.getLevel() == MsgLevel.URGENT) {
            return "URGENT";
        }
        Object result = command.getExtras() == null ? null : command.getExtras().get("result");
        if ("PASSED".equals(String.valueOf(result))) {
            return "PASSED";
        }
        if ("REJECTED".equals(String.valueOf(result))) {
            return "REJECTED";
        }
        return null;
    }

    private static String tagText(String tagCode) {
        if (tagCode == null) {
            return null;
        }
        return switch (tagCode) {
            case "IMPORTANT" -> "重要";
            case "URGENT" -> "紧急";
            case "RECALLED" -> "已停用";
            case "PASSED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> CodeEnum.textOf(MsgLevel.class, tagCode);
        };
    }

    /** 供其它模块按用户查推送设备时复用（推送任务后续接入） */
    public PushRecord latestPush(Long messageId) {
        return pushRecordMapper.selectOne(Wrappers.<PushRecord>lambdaQuery()
                .eq(PushRecord::getMessageId, messageId)
                .orderByDesc(PushRecord::getId)
                .last("LIMIT 1"));
    }

    /** 推送 extras 的键顺序固定，便于测试断言 */
    static Map<String, Object> extras(Long farmId, String bizType, Long bizId, Long messageId, Long todoId,
                                      String channel, String route) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("route", route);
        extras.put("farm_id", farmId);
        extras.put("biz_type", bizType);
        extras.put("biz_id", bizId);
        extras.put("message_id", messageId);
        extras.put("todo_id", todoId);
        extras.put("channel", channel);
        return extras;
    }
}
