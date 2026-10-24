package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.query.BannedDrugQuery;
import com.zhimu.server.admin.base.request.BannedDrugCreateRequest;
import com.zhimu.server.admin.base.request.BannedDrugUpdateRequest;
import com.zhimu.server.admin.base.vo.BannedDrugVO;
import com.zhimu.server.admin.base.vo.BannedHitRowVO;
import com.zhimu.server.admin.base.vo.BannedImpactVO;
import com.zhimu.server.common.api.PageQuery;
import com.zhimu.server.common.api.PageVO;

/** ADM-D30–D34 禁用药清单、影响、列入、编辑、拦截记录。 */
public interface BannedDrugAdminService {

    PageVO<BannedDrugVO> list(BannedDrugQuery query);

    BannedImpactVO impact(Long catalogId);

    BannedDrugVO create(BannedDrugCreateRequest request);

    BannedDrugVO update(Long id, BannedDrugUpdateRequest request);

    PageVO<BannedHitRowVO> hits(Long bannedDrugId, PageQuery query);
}
