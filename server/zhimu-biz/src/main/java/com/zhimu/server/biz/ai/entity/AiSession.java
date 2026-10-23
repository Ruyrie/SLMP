package com.zhimu.server.biz.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhimu.server.framework.mybatis.AuditEntity;
import com.zhimu.server.framework.mybatis.FarmScoped;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * AI 会话（t_ai_session）。
 * batchSnapshotJson 保存关联批次快照：模块04 未接入时批次表不存在，只能按快照展示；接入后由批次服务刷新。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("t_ai_session")
@FarmScoped
public class AiSession extends AuditEntity {

    private Long farmId;
    private Long userId;
    private String title;
    private String topic;
    private Long batchId;
    private String batchSnapshotJson;
    private String batchLinkState;
    private Integer roundCount;
    private Integer contextTruncated;
    private LocalDateTime lastAskedAt;
    private Integer hasCorrection;
    private Integer hasEpidemicAlert;
}
