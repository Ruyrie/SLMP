package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.query.AdminKnowledgeQuery;
import com.zhimu.server.admin.content.request.KnowledgePublishRequest;
import com.zhimu.server.admin.content.request.KnowledgeRollbackRequest;
import com.zhimu.server.admin.content.request.KnowledgeSaveRequest;
import com.zhimu.server.admin.content.request.OffShelfRequest;
import com.zhimu.server.admin.content.vo.KnowledgeEditVO;
import com.zhimu.server.admin.content.vo.KnowledgePageVO;
import com.zhimu.server.admin.content.vo.KnowledgeVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionItemVO;
import com.zhimu.server.admin.content.vo.KnowledgeVersionVO;

import java.util.List;

/** 知识库管理（ADM-C44–C56）。 */
public interface KnowledgeAdminService {

    KnowledgePageVO page(AdminKnowledgeQuery query);

    KnowledgeEditVO edit(Long id);

    KnowledgeVO create(KnowledgeSaveRequest request);

    KnowledgeVO saveDraft(Long id, KnowledgeSaveRequest request);

    KnowledgeVO publish(Long id, KnowledgePublishRequest request);

    List<KnowledgeVersionItemVO> versions(Long id);

    KnowledgeVersionVO version(Long id, Integer versionNo);

    KnowledgeVO rollback(Long id, KnowledgeRollbackRequest request);

    KnowledgeVO offShelf(Long id, OffShelfRequest request);

    KnowledgeVO onShelf(Long id, Integer version);

    void deleteDraft(Long id, Integer version);
}
