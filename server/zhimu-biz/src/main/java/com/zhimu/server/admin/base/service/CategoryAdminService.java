package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.request.CategorySaveRequest;
import com.zhimu.server.admin.base.vo.CategoryVO;

/** ADM-D02 / D03 品类新增与编辑（含停用、启用）。 */
public interface CategoryAdminService {

    CategoryVO create(CategorySaveRequest request);

    CategoryVO update(Long id, CategorySaveRequest request);
}
