package com.zhimu.server.biz.today.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.biz.farm.api.FarmQueryService;
import com.zhimu.server.biz.today.convert.MessageConvert;
import com.zhimu.server.biz.today.entity.Message;
import com.zhimu.server.biz.today.entity.Todo;
import com.zhimu.server.biz.today.mapper.MessageMapper;
import com.zhimu.server.biz.today.mapper.TodoMapper;
import com.zhimu.server.biz.today.query.MessageQuery;
import com.zhimu.server.biz.today.query.UnreadCountQuery;
import com.zhimu.server.biz.today.request.MessageOpenRequest;
import com.zhimu.server.biz.today.request.MessageReadAllRequest;
import com.zhimu.server.biz.today.service.MessageQueryService;
import com.zhimu.server.biz.today.support.TodayJson;
import com.zhimu.server.biz.today.support.TodayText;
import com.zhimu.server.biz.today.support.TodoLinks;
import com.zhimu.server.biz.today.support.TodoScope;
import com.zhimu.server.biz.today.vo.LinkVO;
import com.zhimu.server.biz.today.vo.MessageBriefVO;
import com.zhimu.server.biz.today.vo.MessageDeleteResult;
import com.zhimu.server.biz.today.vo.MessageDetailVO;
import com.zhimu.server.biz.today.vo.MessagePageVO;
import com.zhimu.server.biz.today.vo.OpenResultVO;
import com.zhimu.server.biz.today.vo.ReadAllResult;
import com.zhimu.server.biz.today.vo.UnreadCountVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.CodeEnum;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.enums.MsgCategory;
import com.zhimu.server.common.enums.MsgType;
import com.zhimu.server.common.enums.TodoStatus;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.FarmBriefVO;
import com.zhimu.server.framework.security.spi.FarmMemberLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息中心（API-T12–T18）：只按当前用户；列表 sort_time 倒序、180 天、未隐藏未删除；
 * date_group 取 TODAY / YESTERDAY / EARLIER；打开按 2.6.5 解析去向并标已读；删除软删除且 60 秒内可撤销。
 */
@Service
@RequiredArgsConstructor
public class MessageQueryServiceImpl implements MessageQueryService {

    private static final int RETENTION_DAYS = 180;
    private static final int UNDO_SECONDS = 60;

    private final MessageMapper messageMapper;
    private final TodoMapper todoMapper;
    private final MessageConvert messageConvert;
    private final FarmQueryService farmQueryService;
    private final FarmMemberLoader memberLoader;
    private final TodoScope todoScope;
    private final TodoLinks todoLinks;

    // ===================== API-T12 列表 =====================

    @Override
    public MessagePageVO list(MessageQuery query, Long userId) {
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        String category = validateCategory(query.getCategory());
        LocalDateTime now = BeijingTime.now();
        LocalDate today = BeijingTime.today();

        IPage<Message> paged = messageMapper.selectPage(new Page<>(page, size),
                listWrapper(userId, category, query.getFarmId(), Boolean.TRUE.equals(query.getUnreadOnly()), now));
        Map<Long, FarmBriefVO> farms = farms(userId);
        List<MessageBriefVO> list = new ArrayList<>();
        for (Message message : paged.getRecords()) {
            list.add(messageConvert.toBrief(message,
                    farmOf(message, farms), today, now));
        }
        MessagePageVO vo = new MessagePageVO(list, paged.getTotal(), page, size);
        vo.setFilteredUnread(filteredUnread(userId, category, query.getFarmId(), now));
        return vo;
    }

    // ===================== API-T13 未读数 =====================

    @Override
    public UnreadCountVO unreadCount(UnreadCountQuery query, Long userId) {
        String category = validateCategory(query.getCategory());
        LocalDateTime now = BeijingTime.now();
        UnreadCountVO vo = new UnreadCountVO();
        vo.setTotalUnread(totalUnread(userId));
        vo.setFilteredUnread(filteredUnread(userId, category, query.getFarmId(), now));
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        List<Message> unread = messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .select(Message::getCategory)
                .eq(Message::getUserId, userId)
                .eq(Message::getHidden, false)
                .isNull(Message::getReadAt)
                .ge(Message::getSortTime, now.minusDays(RETENTION_DAYS)));
        for (Message message : unread) {
            byCategory.merge(message.getCategory(), 1, Integer::sum);
        }
        vo.setByCategory(byCategory);
        return vo;
    }

    // ===================== API-T14 全部已读 =====================

    @Override
    @Transactional
    public ReadAllResult readAll(MessageReadAllRequest request, Long userId) {
        String category = validateCategory(request == null ? null : request.getCategory());
        Long farmId = request == null ? null : request.getFarmId();
        LocalDateTime now = BeijingTime.now();
        int updated = messageMapper.update(null, Wrappers.<Message>update()
                .set("read_at", now)
                .eq("user_id", userId)
                .eq("hidden", false)
                .isNull("read_at")
                .ge("sort_time", now.minusDays(RETENTION_DAYS))
                .eq(category != null, "category", category)
                .eq(farmId != null, "farm_id", farmId));
        ReadAllResult result = new ReadAllResult();
        result.setUpdatedCount(updated);
        result.setTotalUnread(totalUnread(userId));
        return result;
    }

    // ===================== API-T15 打开 =====================

    @Override
    @Transactional
    public OpenResultVO open(Long messageId, MessageOpenRequest request, Long userId) {
        Message message = requireMessage(messageId, userId);
        markRead(message, userId);
        OpenResultVO result = resolve(message, userId);
        result.setTotalUnread(totalUnread(userId));
        return result;
    }

    // ===================== API-T16 详情 =====================

    @Override
    @Transactional
    public MessageDetailVO detail(Long messageId, Long userId) {
        Message message = requireMessage(messageId, userId);
        markRead(message, userId);
        MessageBriefVO brief = messageConvert.toBrief(message, farmOf(message, farms(userId)),
                BeijingTime.today(), BeijingTime.now());
        OpenResultVO open = resolve(message, userId);
        open.setTotalUnread(totalUnread(userId));
        return messageConvert.toDetail(brief, message, open);
    }

    // ===================== API-T17 删除 =====================

    @Override
    @Transactional
    public MessageDeleteResult delete(Long messageId, Long userId) {
        Message message = messageMapper.selectIncludingDeleted(messageId, userId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        MessageDeleteResult result = new MessageDeleteResult();
        if (Boolean.TRUE.equals(message.getDeleted()) && message.getDeletedAt() != null) {
            result.setDeletedAt(TodayText.dateTime(message.getDeletedAt()));
            result.setUndoDeadline(TodayText.dateTime(message.getDeletedAt().plusSeconds(UNDO_SECONDS)));
            return result;
        }
        LocalDateTime now = BeijingTime.now();
        messageMapper.markDeleted(messageId, userId, now);
        result.setDeletedAt(TodayText.dateTime(now));
        result.setUndoDeadline(TodayText.dateTime(now.plusSeconds(UNDO_SECONDS)));
        return result;
    }

    // ===================== API-T18 撤销删除 =====================

    @Override
    @Transactional
    public MessageBriefVO restore(Long messageId, Long userId) {
        Message message = messageMapper.selectIncludingDeleted(messageId, userId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(message.getDeleted())) {
            return messageConvert.toBrief(message, farmOf(message, farms(userId)),
                    BeijingTime.today(), BeijingTime.now());
        }
        LocalDateTime deletedAt = message.getDeletedAt();
        if (deletedAt != null && BeijingTime.now().isAfter(deletedAt.plusSeconds(UNDO_SECONDS))) {
            throw new BizException(ErrorCode.STATE_CHANGED, "已超过撤销时限");
        }
        messageMapper.restoreDeleted(messageId, userId);
        Message restored = messageMapper.selectById(messageId);
        return messageConvert.toBrief(restored, farmOf(restored, farms(userId)),
                BeijingTime.today(), BeijingTime.now());
    }

    // ===================== 内部 =====================

    private Message requireMessage(Long messageId, Long userId) {
        Message message = messageMapper.selectIncludingDeleted(messageId, userId);
        if (message == null || Boolean.TRUE.equals(message.getDeleted())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return message;
    }

    private void markRead(Message message, Long userId) {
        if (message.getReadAt() != null) {
            return;
        }
        LocalDateTime now = BeijingTime.now();
        message.setReadAt(now);
        Message update = new Message();
        update.setId(message.getId());
        update.setReadAt(now);
        messageMapper.update(update, Wrappers.<Message>lambdaUpdate().eq(Message::getId, message.getId()));
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Message> listWrapper(
            Long userId, String category, Long farmId, boolean unreadOnly, LocalDateTime now) {
        return Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getHidden, false)
                .ge(Message::getSortTime, now.minusDays(RETENTION_DAYS))
                .eq(category != null, Message::getCategory, category)
                .eq(farmId != null, Message::getFarmId, farmId)
                .isNull(unreadOnly, Message::getReadAt)
                .orderByDesc(Message::getSortTime)
                .orderByDesc(Message::getId);
    }

    private int totalUnread(Long userId) {
        Long count = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getHidden, false)
                .isNull(Message::getReadAt)
                .ge(Message::getSortTime, BeijingTime.now().minusDays(RETENTION_DAYS)));
        return count == null ? 0 : count.intValue();
    }

    private int filteredUnread(Long userId, String category, Long farmId, LocalDateTime now) {
        Long count = messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .eq(Message::getHidden, false)
                .isNull(Message::getReadAt)
                .ge(Message::getSortTime, now.minusDays(RETENTION_DAYS))
                .eq(category != null, Message::getCategory, category)
                .eq(farmId != null, Message::getFarmId, farmId));
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, FarmBriefVO> farms(Long userId) {
        Map<Long, FarmBriefVO> map = new HashMap<>();
        for (FarmBriefVO farm : farmQueryService.listFarmsOfUser(userId)) {
            map.put(farm.getFarmId(), farm);
        }
        return map;
    }

    private FarmBriefVO farmOf(Message message, Map<Long, FarmBriefVO> farms) {
        if (message.getFarmId() == null) {
            return null;
        }
        FarmBriefVO farm = farms.get(message.getFarmId());
        if (farm != null) {
            return farm;
        }
        Map<String, Object> extra = TodayJson.readMap(message.getExtraJson());
        String name = TodayJson.str(extra, "farm_name");
        if (name == null) {
            return null;
        }
        return FarmBriefVO.builder()
                .farmId(message.getFarmId())
                .name(name)
                .status(null)
                .statusText(null)
                .build();
    }

    /** 2.6.5：会员、冻结、记录删除、无权、去向 */
    private OpenResultVO resolve(Message message, Long userId) {
        OpenResultVO vo = new OpenResultVO();
        MsgType type = CodeEnum.of(MsgType.class, message.getMsgType());
        if (type == MsgType.FARM_INVITE || type == MsgType.MEMBER_REMOVED || type == MsgType.FARM_DISABLED
                || type == MsgType.FARM_DELETED || isRevoked(message)) {
            return noJump(vo);
        }

        FarmContext.FarmMember farmMember = message.getFarmId() == null ? null
                : memberLoader.load(message.getFarmId(), userId);
        if (message.getFarmId() != null) {
            if (farmMember == null) {
                return status(vo, "NOT_MEMBER", "你已不是该养殖场的成员", null, null);
            }
            if (farmMember.farmStatus() == FarmStatus.FROZEN) {
                return status(vo, "FARM_FROZEN", "养殖场已被平台冻结，暂时不能使用。原因见消息中心", null, null);
            }
        }

        if (message.getTodoId() != null) {
            return resolveTodo(message, userId, vo, farmMember);
        }

        String route = message.getRoute();
        if (route == null || route.isBlank()) {
            return noJump(vo);
        }
        Map<String, Object> extra = TodayJson.readMap(message.getExtraJson());
        Map<String, String> params = new LinkedHashMap<>();
        Long bizId = message.getBizId();
        if (type == MsgType.AI_DIAGNOSIS) {
            params.put("report_id", String.valueOf(bizId));
        } else if (type == MsgType.CHANGE_REQ_RESULT) {
            params.put("request_id", String.valueOf(bizId));
        } else if (type == MsgType.REPORT_READY) {
            params.put("report_id", String.valueOf(bizId));
        } else if (type == MsgType.LOT_RECALLED) {
            params.put("lot_id", String.valueOf(bizId));
        } else if (type == MsgType.FARM_AUDIT_RESULT || type == MsgType.FARM_CHANGE_RESULT) {
            params.put("farm_id", String.valueOf(message.getFarmId() == null ? bizId : message.getFarmId()));
        } else if (type == MsgType.WITHDRAWAL_DELAYED || type == MsgType.STAFF_NOTIFY) {
            String batchId = TodayJson.str(extra, "batch_id");
            if (batchId != null) {
                params.put("batch_id", batchId);
            }
        } else if (type == MsgType.MEMBER_ROLE_CHANGED || type == MsgType.FARM_ENABLED
                || type == MsgType.FARM_UNFROZEN) {
            params.put("farm_id", String.valueOf(message.getFarmId()));
        }
        vo.setTargetStatus("OK");
        vo.setTargetStatusText("可以打开");
        vo.setLink(TodoLinks.link(route, message.getFarmId(),
                farmMember == null ? null : farmMember.farmName(), params));
        return vo;
    }

    private OpenResultVO resolveTodo(Message message, Long userId, OpenResultVO vo,
                                     FarmContext.FarmMember farmMember) {
        Todo todo = todoMapper.selectIncludingDeleted(message.getTodoId());
        if (todo == null || Boolean.TRUE.equals(todo.getDeleted())) {
            boolean batch = "BATCH".equals(message.getBizType());
            vo.setTargetStatus("DELETED");
            vo.setTargetStatusText("记录已删除");
            vo.setDeletedRecordType(batch ? "BATCH" : "TODO");
            vo.setPageTitle(batch ? "批次档案" : "待办详情");
            vo.setDeletedTitle(batch ? "该批次记录已被删除，无法查看" : "该待办记录已被删除，无法查看");
            vo.setDeletedDesc(batch
                    ? "这条消息关联的批次已被删除，相关的待办和记录不再显示。消息本身仍保留在消息中心。"
                    : "这条消息关联的待办已被删除，相关记录不再显示。消息本身仍保留在消息中心。");
            return vo;
        }
        FarmContext.FarmMember member = farmMember != null ? farmMember
                : memberLoader.load(todo.getFarmId(), userId);
        if (member == null) {
            return status(vo, "NOT_MEMBER", "你已不是该养殖场的成员", null, null);
        }
        if (member.isStaff() && !todoScope.canView(todo, member)) {
            return status(vo, "NO_PERMISSION", "你没有这个栏舍的查看权限", null, null);
        }
        vo.setTargetStatus("OK");
        vo.setTargetStatusText("可以打开");
        LinkVO link;
        if (TodoStatus.PENDING.name().equals(todo.getStatus())) {
            link = todoLinks.listLink(todo, member.farmName());
        } else {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("todo_id", String.valueOf(todo.getId()));
            link = TodoLinks.link("T04", todo.getFarmId(), member.farmName(), params);
        }
        vo.setLink(link);
        return vo;
    }

    private static OpenResultVO noJump(OpenResultVO vo) {
        vo.setTargetStatus("NO_JUMP");
        vo.setTargetStatusText("不跳转");
        return vo;
    }

    private static OpenResultVO status(OpenResultVO vo, String status, String denyTitle,
                                       String deletedTitle, String deletedDesc) {
        vo.setTargetStatus(status);
        vo.setTargetStatusText(statusText(status));
        vo.setDenyTitle(denyTitle);
        vo.setDenyDesc(denyTitle);
        vo.setDeletedTitle(deletedTitle);
        vo.setDeletedDesc(deletedDesc);
        return vo;
    }

    private static String statusText(String status) {
        return switch (status) {
            case "OK" -> "可以打开";
            case "DELETED" -> "记录已删除";
            case "NO_PERMISSION" -> "无权查看";
            case "NOT_MEMBER" -> "已不是成员";
            case "FARM_FROZEN" -> "养殖场已冻结";
            default -> "不跳转";
        };
    }

    private static boolean isRevoked(Message message) {
        return Boolean.TRUE.equals(message.getHidden()) && "NOTICE_REVOKED".equals(message.getHiddenReason());
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String value = category.trim().toUpperCase();
        if (CodeEnum.of(MsgCategory.class, value) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息分类不正确");
        }
        return value;
    }

    /** 仅供测试与后续按天分组复用 */
    static LocalDate groupDate(LocalDateTime sortTime, LocalDate today) {
        return sortTime == null ? null : sortTime.toLocalDate();
    }
}
