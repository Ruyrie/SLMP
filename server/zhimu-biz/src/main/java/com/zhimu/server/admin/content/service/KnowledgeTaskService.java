package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.query.KnowledgeTaskQuery;
import com.zhimu.server.admin.content.request.KnowledgeTaskCreateRequest;
import com.zhimu.server.admin.content.request.TaskCloseRequest;
import com.zhimu.server.admin.content.vo.KnowledgeTaskVO;
import com.zhimu.server.common.api.PageVO;

/** 知识补充任务（ADM-C54–C56）。 */
public interface KnowledgeTaskService {

    PageVO<KnowledgeTaskVO> page(KnowledgeTaskQuery query);

    KnowledgeTaskVO create(KnowledgeTaskCreateRequest request);

    KnowledgeTaskVO close(Long id, TaskCloseRequest request);

    /** 目标条目发布新版本后，把待处理任务置为完成 */
    void completeForKnowledge(Long knowledgeId, Integer versionNo);
}
