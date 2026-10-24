package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.request.LotRecallRequest;
import com.zhimu.server.admin.base.vo.DrugLotRecallVO;
import com.zhimu.server.admin.base.vo.RecallFarmVO;
import com.zhimu.server.admin.base.vo.RecallPreviewVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;

/** ADM-D35–D38 批号停用：列表、预览、停用、处理进度。 */
public interface DrugLotRecallAdminService {

    PageVO<DrugLotRecallVO> list(PageQuery query);

    RecallPreviewVO preview(Long catalogId, String lotNo);

    DrugLotRecallVO recall(LotRecallRequest request);

    PageVO<RecallFarmVO> farms(Long recallId, PageQuery query);
}
