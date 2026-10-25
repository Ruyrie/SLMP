package com.zhimu.server.admin.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.admin.content.entity.Notice;
import com.zhimu.server.admin.content.entity.NoticeTarget;
import com.zhimu.server.admin.content.mapper.ContentLookupMapper;
import com.zhimu.server.admin.content.mapper.NoticeMapper;
import com.zhimu.server.admin.content.mapper.NoticeTargetMapper;
import com.zhimu.server.biz.common.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户持有的消息校验通过后，读取通知的当前内容；不把会过期的文件地址存进消息快照。 */
@Service
@RequiredArgsConstructor
public class NoticeMessageQueryService {
    private final NoticeMapper notices;
    private final NoticeTargetMapper targets;
    private final ContentLookupMapper names;
    private final NoticeAudienceService audience;
    private final FileService files;

    public Set<Long> revokedIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        return notices.selectList(Wrappers.<Notice>lambdaQuery().select(Notice::getId)
                        .in(Notice::getId, ids).eq(Notice::getStatus, "REVOKED"))
                .stream().map(Notice::getId).collect(Collectors.toSet());
    }

    /** 返回 null 时沿用历史消息的完整快照，兼容旧数据。 */
    public Map<String, Object> payload(Long id) {
        Notice notice = id == null ? null : notices.selectById(id);
        if (notice == null) return null;
        boolean revoked = "REVOKED".equals(notice.getStatus());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notice_id", notice.getId());
        payload.put("level", notice.getLevel());
        payload.put("title", notice.getTitle());
        payload.put("sent_at", notice.getSentAt());
        payload.put("revoked", revoked);
        payload.put("revoked_at", notice.getRevokedAt());
        String sender = "智牧通平台";
        if (notice.getSentBy() != null) {
            var rows = names.selectAdminNames(List.of(notice.getSentBy()));
            if (rows != null && !rows.isEmpty() && rows.getFirst().getName() != null) sender = rows.getFirst().getName();
        }
        payload.put("sender_name", sender);
        List<Long> farmIds = targets.selectList(Wrappers.<NoticeTarget>lambdaQuery()
                        .eq(NoticeTarget::getNoticeId, id).eq(NoticeTarget::getTargetType, "FARM"))
                .stream().map(NoticeTarget::getTargetValue).filter(value -> value != null && value.matches("\\d+"))
                .map(Long::valueOf).toList();
        payload.put("target_farm_names", audience.farms(farmIds).stream().map(farm -> farm.getName()).toList());
        // 已读、未读接收人都读取当前撤回状态；撤回后不再签发原图或返回正文。
        if (!revoked) {
            payload.put("content", notice.getContent());
            if (notice.getImageFileId() != null) payload.put("image", files.fileVo(notice.getImageFileId()));
        }
        return payload;
    }
}
