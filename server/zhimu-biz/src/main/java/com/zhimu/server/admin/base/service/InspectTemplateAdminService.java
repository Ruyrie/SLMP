package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.request.InspectItemSaveRequest;
import com.zhimu.server.admin.base.request.InspectTemplateCreateRequest;
import com.zhimu.server.admin.base.request.TemplateItemOrderRequest;
import com.zhimu.server.admin.base.request.TemplatePublishRequest;
import com.zhimu.server.admin.base.request.TemplateRollbackRequest;
import com.zhimu.server.admin.base.vo.InspectItemVO;
import com.zhimu.server.admin.base.vo.InspectTemplateListVO;
import com.zhimu.server.admin.base.vo.InspectTemplateVO;
import com.zhimu.server.admin.base.vo.TemplateVersionVO;

import java.util.List;

/** ADM-D39–D48 巡检项目模板：按品类列表、详情、草稿项目、发布、放弃、版本与回退。 */
public interface InspectTemplateAdminService {

    List<InspectTemplateListVO> list();

    InspectTemplateVO detail(Long templateId);

    InspectTemplateVO create(InspectTemplateCreateRequest request);

    InspectItemVO addItem(Long templateId, InspectItemSaveRequest request);

    InspectItemVO updateItem(Long templateId, Long itemId, InspectItemSaveRequest request);

    void deleteItem(Long templateId, Long itemId, Integer version);

    void order(Long templateId, TemplateItemOrderRequest request);

    TemplateVersionVO<InspectItemVO> publish(Long templateId, TemplatePublishRequest request);

    void discard(Long templateId, Integer version);

    List<TemplateVersionVO<InspectItemVO>> versions(Long templateId);

    /** 回退：把历史版本项目复制为新草稿，发布后生效 */
    InspectTemplateVO rollback(Long templateId, TemplateRollbackRequest request);
}
