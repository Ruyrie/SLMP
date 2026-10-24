package com.zhimu.server.biz.ai.convert;

import com.zhimu.server.biz.ai.entity.AiSession;
import com.zhimu.server.biz.ai.mapper.AiMessageMapper;
import com.zhimu.server.biz.ai.support.AiBatchContextService;
import com.zhimu.server.biz.ai.support.AiCodes;
import com.zhimu.server.biz.ai.support.AiText;
import com.zhimu.server.biz.ai.vo.AiSessionDetailVO;
import com.zhimu.server.biz.ai.vo.AiSessionItemVO;
import com.zhimu.server.biz.ai.vo.LinkedBatchVO;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话 VO 组装。
 */
@Component
@RequiredArgsConstructor
public class AiSessionConvert {

    private final AiBatchContextService batchContextService;
    private final AiMessageMapper messageMapper;

    public AiSessionItemVO toItem(AiSession session) {
        AiSessionItemVO vo = new AiSessionItemVO();
        vo.setId(session.getId());
        vo.setTitle(displayTitle(session));
        vo.setTopic(session.getTopic());
        vo.setTopicText(null);
        LinkedBatchVO batch = batchContextService.fromSnapshot(session.getBatchSnapshotJson());
        vo.setBatchNo(batch == null ? null : batch.getBatchNo());
        vo.setRoundCount(session.getRoundCount() == null ? 0 : session.getRoundCount());
        vo.setLastAskedAt(BeijingTime.format(session.getLastAskedAt()));
        String group = AiText.dateGroup(session.getLastAskedAt());
        vo.setDateGroup(group);
        vo.setDateGroupText(AiText.dateGroupText(group));
        vo.setHasCorrection(isTrue(session.getHasCorrection()));
        vo.setContextTruncated(isTrue(session.getContextTruncated()));
        vo.setHasEpidemicAlert(isTrue(session.getHasEpidemicAlert()));
        return vo;
    }

    public AiSessionDetailVO toDetail(AiSession session) {
        AiSessionDetailVO vo = new AiSessionDetailVO();
        vo.setId(session.getId());
        vo.setTitle(displayTitle(session));
        int rounds = session.getRoundCount() == null ? 0 : session.getRoundCount();
        vo.setRoundCount(rounds);
        vo.setLastRoundNo(rounds);
        vo.setHasCorrection(isTrue(session.getHasCorrection()));
        vo.setContextTruncated(isTrue(session.getContextTruncated()));
        vo.setHasEpidemicAlert(isTrue(session.getHasEpidemicAlert()));
        String state = session.getBatchLinkState() == null ? AiCodes.LINK_NONE : session.getBatchLinkState();
        vo.setBatchLinkState(state);
        LinkedBatchVO batch = batchContextService.fromSnapshot(session.getBatchSnapshotJson());
        if (AiCodes.LINK_VALID.equals(state) && batch != null) {
            vo.setBatch(batch);
        }
        if (!AiCodes.LINK_NONE.equals(state) && !AiCodes.LINK_VALID.equals(state) && batch != null) {
            vo.setInvalidBatchNo(batch.getBatchNo());
        }
        return vo;
    }

    /** 会话详情里实时计算的最近轮次（避免会话 round_count 与消息表不一致） */
    public int liveRoundNo(Long sessionId) {
        return messageMapper.maxRoundNo(sessionId);
    }

    private static boolean isTrue(Integer value) {
        return value != null && value == 1;
    }

    /** 空会话、标题缺失时统一显示“新对话” */
    private static String displayTitle(AiSession session) {
        String title = session.getTitle();
        return title == null || title.isBlank() ? AiText.NEW_CHAT_TITLE : title;
    }
}
