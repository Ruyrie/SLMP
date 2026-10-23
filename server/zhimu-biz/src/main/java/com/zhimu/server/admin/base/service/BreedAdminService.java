package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.request.BreedSaveRequest;
import com.zhimu.server.admin.base.vo.BreedVO;

/** ADM-D05 / D06 品种新增与编辑（含停用、启用）。 */
public interface BreedAdminService {

    BreedVO create(BreedSaveRequest request);

    BreedVO update(Long id, BreedSaveRequest request);
}
