package com.zhimu.server.biz.today.convert;

import com.zhimu.server.biz.farm.entity.FarmInvite;
import com.zhimu.server.biz.farm.entity.FarmTransfer;
import com.zhimu.server.biz.farm.mapper.FarmInviteMapper;
import com.zhimu.server.biz.farm.mapper.FarmTransferMapper;
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
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 消息 Entity → VO：分类/级别中文、date_group、icon_state、合并次数、邀请/转让动作与详情 payload。
 * 显示时间一律取 sort_time；员工只收到自己可见的消息由写入方控制。
 */
@Component
@RequiredArgsConstructor
public class MessageConvert {

    private final TodoMapper todoMapper;
    private final FarmInviteMapper inviteMapper;
    private final FarmTransferMapper transferMapper;

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
        MessageActionVO action = action(message, now);
        vo.setIconState(iconState(message, now, action));
        vo.setAction(action);
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

    private String iconState(Message message, LocalDateTime now, MessageActionVO action) {
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
        Long bizId = message.getBizId() != null ? message.getBizId()
                : TodayJson.lng(extra, invite ? "invite_id" : "transfer_id");
        action.setActionId(bizId == null ? 0L : bizId);
        // 消息是发送时的快照；邀请接受、拒绝、撤回后必须以业务记录的当前状态为准。
        // 消息已按接收人查询，跨场读取仍核对 farm_id 和绑定的接收人，避免串用其它邀请。
        String status = "CANCELED";
        LocalDateTime expireAt = null;
        String roleText = TodayJson.str(extra, "role_text");
        if (bizId != null && invite) {
            FarmInvite current = TenantSkip.call(() -> inviteMapper.selectById(bizId));
            if (current != null && Objects.equals(current.getFarmId(), message.getFarmId())
                    && (current.getInviteeUserId() == null
                    || Objects.equals(current.getInviteeUserId(), message.getUserId()))) {
                status = current.getStatus();
                expireAt = current.getExpiresAt();
                roleText = "MANAGER".equals(current.getRole()) ? "场长" : "员工";
            }
        } else if (bizId != null) {
            FarmTransfer current = TenantSkip.call(() -> transferMapper.selectById(bizId));
            if (current != null && Objects.equals(current.getFarmId(), message.getFarmId())
                    && Objects.equals(current.getToUserId(), message.getUserId())) {
                status = current.getStatus();
                expireAt = current.getExpiresAt();
                roleText = "所有者";
            }
        }
        if (status == null || status.isBlank()) {
            status = "CANCELED";
        }
        if ("PENDING".equals(status) && expireAt != null && !expireAt.isAfter(now)) {
            status = "EXPIRED";
        }
        action.setStatus(status);
        action.setStatusText(statusText(status, invite, expireAt, now));
        action.setRoleText(roleText);
        action.setExpireAt(expireAt);
        action.setCanAccept("PENDING".equals(status) && (expireAt == null || expireAt.isAfter(now)));
        return action;
    }

    /** 邀请待确认显示剩余天数（“7 天内有效”），转让待确认显示“待你确认”（与 App 列表副文字一致） */
    private static String statusText(String status, boolean invite, LocalDateTime expireAt, LocalDateTime now) {
        return switch (status) {
            case "PENDING" -> {
                if (!invite) {
                    yield "待你确认";
                }
                Long days = daysLeft(expireAt, now);
                yield days == null ? "待确认" : (days <= 0 ? "已过期" : days + " 天内有效");
            }
            case "ACCEPTED" -> "已同意";
            case "REJECTED" -> "已拒绝";
            case "EXPIRED" -> "已过期";
            case "CANCELED" -> "已失效";
            default -> status;
        };
    }

    /** 距到期还有几天（不足一天按一天算）；没有到期时间返回 null */
    private static Long daysLeft(LocalDateTime expireAt, LocalDateTime now) {
        if (expireAt == null) {
            return null;
        }
        long minutes = java.time.Duration.between(now, expireAt).toMinutes();
        if (minutes <= 0) {
            return 0L;
        }
        return (minutes + 1439) / 1440;
    }

}
