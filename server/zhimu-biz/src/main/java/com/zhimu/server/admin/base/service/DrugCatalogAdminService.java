package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.query.DrugCatalogQuery;
import com.zhimu.server.admin.base.request.DrugCatalogSaveRequest;
import com.zhimu.server.admin.base.request.DrugStatusRequest;
import com.zhimu.server.admin.base.vo.DrugCatalogDetailVO;
import com.zhimu.server.admin.base.vo.DrugCatalogRowVO;
import com.zhimu.server.common.api.PageVO;

/** ADM-D24–D28 兽药疫苗目录：列表、详情、新增、编辑、停用 / 启用。 */
public interface DrugCatalogAdminService {

    PageVO<DrugCatalogRowVO> list(DrugCatalogQuery query);

    DrugCatalogDetailVO detail(Long id);

    DrugCatalogDetailVO create(DrugCatalogSaveRequest request);

    UpdateResult update(Long id, DrugCatalogSaveRequest request);

    DrugCatalogDetailVO changeStatus(Long id, DrugStatusRequest request);

    /**
     * @param vo                     更新后的详情
     * @param message                成功文案
     * @param changedWithdrawalCount 休药期有变化的畜种数（0 表示未改休药期）
     */
    record UpdateResult(DrugCatalogDetailVO vo, String message, int changedWithdrawalCount) {
    }
}
