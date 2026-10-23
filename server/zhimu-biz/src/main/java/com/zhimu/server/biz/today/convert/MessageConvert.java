package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.support.TodayJson;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.vo.MessageActionVO;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.MsgCategory;
import com.zhimu.server.common.enums.MsgLevel;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.jackson.ZhimuJackson;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 消息 Entity → VO：分类/级别中文、date_group、icon_state、合并次数、邀请/转让动作与详情 payload。
 * 显示时间一律取 sort_time；员工只收到自己可见的消息由写入方控制。
 */
@Component
@RequiredArgsConstructor
public class MessageConvert {

    private final TodoMapper todoMapper;

    public MessageBriefVO toBrief(Message message, FarmBriefVO farm, LocalDate today, LocalDateTime now) {
        MessageBriefVO vo = new MessageBriefVO();
        vo.setMessageId(message.getId());
        vo.setCategory(message.getCategory());
        vo.setCategoryText(CodeEnum.textOf(MsgCategory.class, message.getCategory()));
        vo.setMsgType(message.getMsgType());
        vo.setLevel(message.getLevel());
        vo.setLevelText(CodeEnum.textOf(MsgLevel.class, message.getLevel()));
        vo.setTitle(message.getTitle());
        Integer mergeCount = message.getMergeCount() == null ? 1 : message.getMergeCount();
        vo.setMergeCount(mergeCount);
        vo.setSummary(mergeCount >= 2 ? "已合并 " + mergeCount + " 次提醒" : message.getSummary());
        vo.setTagText(message.getTagText());
        vo.setTagCode(message.getTagCode());
        vo.setFarm(farm);
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setTodoId(message.getTodoId());
        vo.setRoute(message.getRoute());
        vo.setRead(message.getReadAt() != null);
        vo.setRevoked(isRevoked(message));
        vo.setCreatedAt(message.getCreatedAt());
        vo.setSortTime(message.getSortTime());
        vo.setDateGroup(dateGroup(message.getSortTime(), today));
        vo.setIconState(iconState(message, now));
        vo.setAction(action(message, now));
        return vo;
    }

    public MessageDetailVO toDetail(MessageBriefVO brief, Message message, OpenResultVO open) {
        MessageDetailVO vo = new MessageDetailVO();
        BeanUtils.copyProperties(brief, vo);
        if (open != null) {
            vo.setTargetStatus(open.getTargetStatus());
            vo.setTargetStatusText(open.getTargetStatusText());
            vo.setLink(open.getLink());
            vo.setDeletedRecordType(open.getDeletedRecordType());
            vo.setPageTitle(open.getPageTitle());
            vo.setDeletedTitle(open.getDeletedTitle());
            vo.setDeletedDesc(open.getDeletedDesc());
            vo.setDenyTitle(open.getDenyTitle());
            vo.setDenyDesc(open.getDenyDesc());
            vo.setTotalUnread(open.getTotalUnread());
        }
        Map<String, Object> payload = TodayJson.readMap(message.getExtraJson());
        vo.setPayload(payload.isEmpty() ? null : payload);
        return vo;
    }

    /** TODAY / YESTERDAY / EARLIER */
    public static String dateGroup(LocalDateTime sortTime, LocalDate today) {
        if (sortTime == null) {
            return "EARLIER";
        }
        LocalDate date = sortTime.toLocalDate();
        if (date.equals(today)) {
            return "TODAY";
        }
        if (date.equals(today.minusDays(1))) {
            return "YESTERDAY";
        }
        return "EARLIER";
    }

    private String iconState(Message message, LocalDateTime now) {
        if (message.getTodoId() != null) {
            Todo todo = todoMapper.selectIncludingDeleted(message.getTodoId());
            if (todo != null) {
                return TodayText.displayStatus(todo, now).name();
            }
            return TodoStatus.IGNORED.name();
        }
        if (message.getHidden() != null && message.getHidden()
                && "NOTICE_REVOKED".equals(message.getHiddenReason())) {
            return "DEFAULT";
        }
        MessageActionVO action = action(message, now);
        if (action != null && !"PENDING".equals(action.getStatus())) {
            return "HANDLED";
        }
        return "DEFAULT";
    }

    private static boolean isRevoked(Message message) {
        if (message.getHidden() != null && message.getHidden()
                && "NOTICE_REVOKED".equals(message.getHiddenReason())) {
            return true;
        }
        return TodayJson.bool(TodayJson.readMap(message.getExtraJson()), "revoked");
    }

    /** 邀请与转让的动作块 */
    public MessageActionVO action(Message message, LocalDateTime now) {
        MsgType type = CodeEnum.of(MsgType.class, message.getMsgType());
        if (type == null) {
            return null;
        }
        boolean invite = type == MsgType.FARM_INVITE;
        boolean transfer = type == MsgType.OWNER_TRANSFER;
        if (!invite && !transfer) {
            return null;
        }
        Map<String, Object> extra = TodayJson.readMap(message.getExtraJson());
        MessageActionVO action = new MessageActionVO();
        action.setActionType(invite ? "INVITE" : "TRANSFER");
        Long bizId = message.getBizId() != null ? message.getBizId() : TodayJson.lng(extra, "invite_id");
        action.setActionId(bizId == null ? 0L : bizId);
        String status = TodayJson.str(extra, "status");
        if (status == null || status.isBlank()) {
            status = message.getReadAt() == null ? "PENDING" : "PENDING";
        }
        action.setStatus(status);
        action.setStatusText(statusText(status));
        action.setRoleText(TodayJson.str(extra, "role_text"));
        LocalDateTime expireAt = parseDateTime(TodayJson.str(extra, "expire_at"));
        action.setExpireAt(expireAt);
        action.setCanAccept("PENDING".equals(status) && (expireAt == null || expireAt.isAfter(now)));
        return action;
    }

    private static String statusText(String status) {
        return switch (status) {
            case "PENDING" -> "待确认";
            case "ACCEPTED" -> "已同意";
            case "REJECTED" -> "已拒绝";
            case "EXPIRED" -> "已过期";
            default -> status;
        };
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), ZhimuJackson.DATETIME_INPUT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
