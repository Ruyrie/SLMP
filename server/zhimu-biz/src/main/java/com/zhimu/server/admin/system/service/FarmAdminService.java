package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.query.AdminFarmQuery;
import com.zhimu.server.admin.system.request.FarmFreezeRequest;
import com.zhimu.server.admin.system.vo.AdminFarmDetailVO;
import com.zhimu.server.admin.system.vo.AdminFarmItemVO;
import com.zhimu.server.admin.system.vo.ExportVO;
import com.zhimu.server.admin.system.vo.FreezeResultVO;
import com.zhimu.server.common.api.PageVO;

/** ADM-S15–S19 养殖场管理：列表、详情、冻结/解冻、导出。 */
public interface FarmAdminService {

    PageVO<AdminFarmItemVO> list(AdminFarmQuery query);

    AdminFarmDetailVO detail(Long farmId);

    FreezeResultVO freeze(Long farmId, FarmFreezeRequest request);

    String unfreeze(Long farmId, FarmFreezeRequest request);

    ExportVO export(AdminFarmQuery query);
}
