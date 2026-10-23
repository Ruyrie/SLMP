package com.zhimu.server.biz.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.ai.convert.AiMessageConvert;
import com.zhimu.server.biz.ai.convert.AiSessionConvert;
import com.zhimu.server.biz.ai.entity.AiMessage;
import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.mapper.AiSessionMapper;
import com.zhimu.server.biz.ai.query.AiMessageQuery;
import com.zhimu.server.biz.ai.query.AiSessionQuery;
import com.zhimu.server.biz.ai.service.AiSessionService;
import com.zhimu.server.biz.ai.support.AiBatchContextService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiErrors;
import com.zhimu.server.biz.ai.vo.AiMessageSliceVO;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkableBatchesVO;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.error.Asserts;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * API-K01–K05。
 */
@Service
@RequiredArgsConstructor
public class AiSessionServiceImpl implements AiSessionService {

    private static final int MAX_KEYWORD = 50;
    private static final int MAX_SLICE_SIZE = 50;

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiSessionConvert sessionConvert;
    private final AiMessageConvert messageConvert;
    private final AiBatchContextService batchContextService;

    @Override
    public PageVO<AiSessionItemVO> listSessions(Long userId, AiSessionQuery query) {
        String keyword = query.getKeyword() == null ? null : query.getKeyword().trim();
        if (keyword != null && keyword.length() > MAX_KEYWORD) {
            throw AiErrors.fieldError("keyword", "搜索内容不能超过 50 字");
        }
        int page = query.pageOrDefault();
        int size = query.sizeOrDefault();
        var wrapper = Wrappers.<AiSession>lambdaQuery().eq(AiSession::getUserId, userId);
        if (keyword != null && !keyword.isEmpty()) {
            List<Long> sessionIds = messageMapper.selectSessionIdsByQuestion(userId, keyword);
            wrapper.and(w -> {
                w.like(AiSession::getTitle, keyword);
                if (!sessionIds.isEmpty()) {
                    w.or().in(AiSession::getId, sessionIds);
                }
            });
        }
        long total = sessionMapper.selectCount(wrapper);
        List<AiSession> sessions = sessionMapper.selectList(wrapper
                .orderByDesc(AiSession::getLastAskedAt)
                .orderByDesc(AiSession::getId)
                .last("LIMIT " + size + " OFFSET " + (long) (page - 1) * size));
        List<AiSessionItemVO> list = new ArrayList<>();
        for (AiSession session : sessions) {
            list.add(sessionConvert.toItem(session));
        }
        return PageVO.of(list, total, page, size);
    }

    @Override
    public AiSessionDetailVO detail(Long userId, Long sessionId) {
        AiSession session = requireOwn(userId, sessionId);
        refreshBatchState(session);
        return sessionConvert.toDetail(session);
    }

    @Override
    public AiMessageSliceVO messages(Long userId, Long sessionId, AiMessageQuery query) {
        requireOwn(userId, sessionId);
        if (query.getBeforeRoundNo() != null && query.getAfterRoundNo() != null) {
            throw AiErrors.invalid("参数不正确");
        }
        int size = query.getSize() == null || query.getSize() < 1 ? 20 : Math.min(query.getSize(), MAX_SLICE_SIZE);
        List<AiMessage> messages;
        boolean hasMore;
        if (query.getBeforeRoundNo() != null) {
            int before = query.getBeforeRoundNo();
            messages = new ArrayList<>(messageMapper.selectBeforeRound(sessionId, before, size));
            hasMore = messageMapper.countBeforeRound(sessionId, before) > size;
            messages = reversed(messages);
        } else if (query.getAfterRoundNo() != null) {
            int after = query.getAfterRoundNo();
            messages = messageMapper.selectAfterRound(sessionId, after, size);
            hasMore = messageMapper.countAfterRound(sessionId, after) > size;
        } else {
            messages = new ArrayList<>(messageMapper.selectBeforeRound(sessionId, Integer.MAX_VALUE, size));
            hasMore = messageMapper.countBeforeRound(sessionId, Integer.MAX_VALUE) > size;
            messages = reversed(messages);
        }
        return messageConvert.slice(messages, hasMore);
    }

    @Override
    @Transactional
    public AiSessionDetailVO changeBatch(Long userId, Long sessionId, Long batchId) {
        AiSession session = requireOwn(userId, sessionId);
        FarmContext.FarmMember member = FarmContext.require();
        if (batchId == null) {
            session.setBatchId(null);
            session.setBatchSnapshotJson(null);
            session.setBatchLinkState(AiCodes.LINK_NONE);
        } else {
            LinkedBatchVO batch = batchContextService.resolve(member.farmId(), batchId, member.isStaff(), member.penIds());
            if (batch == null) {
                LinkedBatchVO any = batchContextService.resolve(member.farmId(), batchId, false, member.penIds());
                if (any != null) {
                    throw new BizException(ErrorCode.PEN_FORBIDDEN,
                            "你已不负责这个批次所在的栏舍，之后的提问不再带上该批次的数据");
                }
                throw AiErrors.notFound();
            }
            session.setBatchId(batchId);
            session.setBatchSnapshotJson(batchContextService.toSnapshot(batch));
            session.setBatchLinkState(AiCodes.LINK_VALID);
        }
        sessionMapper.updateById(session);
        return sessionConvert.toDetail(session);
    }

    @Override
    public LinkableBatchesVO linkableBatches(Long userId) {
        FarmContext.FarmMember member = FarmContext.require();
        LinkableBatchesVO vo = new LinkableBatchesVO();
        vo.setBatches(batchContextService.listLinkable(member.farmId(), member.isStaff(), member.penIds()));
        if (member.isStaff()) {
            vo.setPanelHint("只能关联你负责栏舍的在栏批次。提问时会带上批次的品种、日龄、存栏和最近一次称重体重。");
        } else {
            vo.setPanelHint("只能关联" + member.farmName() + "的在栏批次。提问时会带上批次的品种、日龄、存栏和最近一次称重体重。");
        }
        if (vo.getBatches().isEmpty()) {
            vo.setEmptyHint("当前没有可关联的在栏批次");
        }
        return vo;
    }

    private AiSession requireOwn(Long userId, Long sessionId) {
        AiSession session = Asserts.found(sessionMapper.selectById(sessionId));
        if (!userId.equals(session.getUserId())) {
            throw AiErrors.notFound();
        }
        return session;
    }

    /** 批次状态实时计算：快照存在但批次已不可解析时标为已不在栏 */
    private void refreshBatchState(AiSession session) {
        if (session.getBatchId() == null || !AiCodes.LINK_VALID.equals(session.getBatchLinkState())) {
            return;
        }
        FarmContext.FarmMember member = FarmContext.get();
        if (member == null) {
            return;
        }
        LinkedBatchVO batch = batchContextService.resolve(member.farmId(), session.getBatchId(), false, member.penIds());
        if (batch == null) {
            session.setBatchLinkState(AiCodes.LINK_NOT_IN_STOCK);
        }
    }

    private static List<AiMessage> reversed(List<AiMessage> messages) {
        List<AiMessage> result = new ArrayList<>(messages);
        java.util.Collections.reverse(result);
        return result;
    }

    /** 建会话时使用：现在时间 */
    public static LocalDateTime now() {
        return BeijingTime.now();
    }
}
