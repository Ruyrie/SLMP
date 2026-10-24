package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.query.ImmuneTemplateQuery;
import com.zhimu.server.admin.base.request.ImmuneItemSaveRequest;
import com.zhimu.server.admin.base.request.ImmuneTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.vo.ImmuneItemVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateGroupVO;
import com.zhimu.server.admin.base.vo.ImmuneTemplateVO;
import com.zhimu.server.admin.base.vo.TemplateVersionVO;

import java.util.List;

/** ADM-D14–D23 免疫程序模板：列表、详情、草稿项目、发布、放弃、版本与回退。 */
public interface ImmuneTemplateAdminService {

    List<ImmuneTemplateGroupVO> list(ImmuneTemplateQuery query);

    ImmuneTemplateVO detail(Long templateId);

    ImmuneTemplateVO create(ImmuneTemplateCreateRequest request);

    ImmuneItemVO addItem(Long templateId, ImmuneItemSaveRequest request);

    ImmuneItemVO updateItem(Long templateId, Long itemId, ImmuneItemSaveRequest request);

    void deleteItem(Long templateId, Long itemId, Integer version);

    void order(Long templateId, TemplateItemOrderRequest request);

    TemplateVersionVO<ImmuneItemVO> publish(Long templateId, TemplatePublishRequest request);

    void discard(Long templateId, Integer version);

    List<TemplateVersionVO<ImmuneItemVO>> versions(Long templateId);

    /** 回退：把历史版本项目复制为新草稿，发布后生效 */
    ImmuneTemplateVO rollback(Long templateId, TemplateRollbackRequest request);
}
