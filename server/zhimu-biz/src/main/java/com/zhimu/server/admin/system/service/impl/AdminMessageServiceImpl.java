package com.zhimu.server.admin.system.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.system.api.AdminMessageCommand;
import com.zhimu.server.admin.system.api.AdminMessageService;
import com.zhimu.server.admin.system.entity.Admin;
import com.zhimu.server.admin.system.entity.AdminMessage;
import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.AdminMessageMapper;
import com.zhimu.server.admin.system.query.AdminMessageQuery;
import com.zhimu.server.admin.system.request.MessageReadRequest;
import com.zhimu.server.admin.system.support.AdminLogTexts;
import com.zhimu.server.admin.system.support.AdminPermissionCache;
import com.zhimu.server.admin.system.vo.AdminMessageVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.framework.jackson.CacheJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端站内消息：写入（含 merge_key 去重）、按权限广播、未读数、本人消息分页与标记已读。
 * 只读写本人消息；sendToPermission 发给全部正常管理员，超管通过角色权限缓存自然命中全部权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMessageServiceImpl implements AdminMessageService {

    private static final TypeReference<LinkedHashMap<String, Object>> PARAMS_TYPE = new TypeReference<>() {
    };

    private final AdminMessageMapper adminMessageMapper;
    private final AdminMapper adminMapper;
    private final AdminPermissionCache permissionCache;

    @Override
    public void send(AdminMessageCommand command) {
        if (command == null || command.getAdminId() == null) {
            return;
        }
        saveForAdmin(command.getAdminId(), command);
    }

    @Override
    public void sendToPermission(String permCode, AdminMessageCommand command) {
        if (permCode == null || permCode.isBlank() || command == null) {
            return;
        }
        List<Admin> admins = adminMapper.selectAllNormal();
        if (admins == null || admins.isEmpty()) {
            return;
        }
        for (Admin admin : admins) {
            if (admin.getRoleId() == null) {
                continue;
            }
            if (!permissionCache.permissionsOfRole(admin.getRoleId()).contains(permCode)) {
                continue;
            }
            saveForAdmin(admin.getId(), command);
        }
    }

    @Override
    public int unreadCount() {
        Long adminId = AdminContext.adminId();
        return adminId == null ? 0 : unreadCount(adminId);
    }

    @Override
    public int unreadCount(Long adminId) {
        if (adminId == null) {
            return 0;
        }
        return (int) adminMessageMapper.countUnread(adminId);
    }

    @Override
    public PageVO<AdminMessageVO> page(AdminMessageQuery query) {
        Long adminId = AdminContext.requireAdminId();
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        boolean unreadOnly = "UNREAD".equalsIgnoreCase(query.getRead());
        var wrapper = Wrappers.<AdminMessage>lambdaQuery()
                .eq(AdminMessage::getAdminId, adminId)
                .isNull(unreadOnly, AdminMessage::getReadAt)
                .orderByDesc(AdminMessage::getCreatedAt)
                .orderByDesc(AdminMessage::getId);
        IPage<AdminMessage> result = adminMessageMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminMessageVO> list = new ArrayList<>(result.getRecords().size());
        for (AdminMessage message : result.getRecords()) {
            list.add(toVO(message));
        }
        Map<String, Integer> tabCounts = new LinkedHashMap<>();
        tabCounts.put("all", (int) adminMessageMapper.countAll(adminId));
        tabCounts.put("unread", (int) adminMessageMapper.countUnread(adminId));
        return PageVO.of(list, result.getTotal(), page, size).withTabCounts(tabCounts);
    }

    @Override
    public int markRead(MessageReadRequest request) {
        Long adminId = AdminContext.requireAdminId();
        LocalDateTime now = BeijingTime.now();
        if (request != null && Boolean.TRUE.equals(request.all())) {
            return adminMessageMapper.updateReadAll(adminId, now);
        }
        if (request == null || request.messageIds() == null || request.messageIds().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择要标记的消息");
        }
        return adminMessageMapper.updateReadByIds(adminId, request.messageIds(), now);
    }

    /** 同一接收人同一 merge_key 只保留一条：已存在时更新内容并重新置为未读（提醒类消息再次出现） */
    private void saveForAdmin(Long adminId, AdminMessageCommand command) {
        String mergeKey = trimToNull(command.getMergeKey());
        AdminMessage existing = mergeKey == null ? null : adminMessageMapper.selectByMergeKey(adminId, mergeKey);
        if (existing != null) {
            apply(existing, command);
            existing.setReadAt(null);
            adminMessageMapper.updateById(existing);
            return;
        }
        AdminMessage message = new AdminMessage();
        message.setAdminId(adminId);
        message.setReadAt(null);
        apply(message, command);
        try {
            adminMessageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            // 并发写入同一 merge_key：改为更新已存在的一条
            if (mergeKey == null) {
                throw e;
            }
            AdminMessage duplicated = adminMessageMapper.selectByMergeKey(adminId, mergeKey);
            if (duplicated == null) {
                throw e;
            }
            apply(duplicated, command);
            duplicated.setReadAt(null);
            adminMessageMapper.updateById(duplicated);
        }
    }

    private static void apply(AdminMessage message, AdminMessageCommand command) {
        message.setMsgType(truncate(command.getMsgType(), 30));
        message.setLevel(truncate(command.getLevel() == null ? "NORMAL" : command.getLevel(), 20));
        message.setTitle(truncate(command.getTitle(), 100));
        message.setSummary(truncate(command.getSummary(), 200));
        message.setBizType(truncate(command.getBizType(), 30));
        message.setBizId(command.getBizId());
        message.setRoutePage(truncate(command.getRoutePage(), 10));
        message.setRouteParams(command.getRouteParams() == null || command.getRouteParams().isEmpty()
                ? null : CacheJson.write(command.getRouteParams()));
        message.setMergeKey(truncate(command.getMergeKey(), 100));
    }

    private static AdminMessageVO toVO(AdminMessage message) {
        Map<String, Object> routeParams = null;
        if (message.getRouteParams() != null && !message.getRouteParams().isBlank()) {
            try {
                routeParams = CacheJson.read(message.getRouteParams(), PARAMS_TYPE);
            } catch (RuntimeException e) {
                log.debug("消息 route_params 解析失败 id={}", message.getId(), e);
            }
        }
        return AdminMessageVO.builder()
                .messageId(message.getId())
                .msgType(message.getMsgType())
                .msgTypeText(AdminLogTexts.msgTypeText(message.getMsgType()))
                .level(message.getLevel())
                .title(message.getTitle())
                .summary(message.getSummary())
                .bizType(message.getBizType())
                .bizId(message.getBizId())
                .routePage(message.getRoutePage())
                .routeParams(routeParams)
                .read(message.getReadAt() != null)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
