package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.request.DrugSaveRequest;
import com.zhimu.server.biz.stock.vo.DrugArchiveListResult;
import com.zhimu.server.biz.stock.vo.DrugDetailVO;
import com.zhimu.server.biz.stock.vo.DrugOptionListResult;
import com.zhimu.server.biz.stock.vo.DrugStockOverviewVO;

/** 兽药疫苗：概览、档案列表 / 下拉 / 详情 / 建档（API-M24、M34–M38）。 */
public interface StockDrugService {

    /** API-M24 兽药疫苗概览 */
    DrugStockOverviewVO overview();

    /** API-M34 药品档案列表（不分页，前端按类别分组） */
    DrugArchiveListResult list(String keyword, String category);

    /** API-M35 药品下拉：INBOUND 已建档且非禁用药 / OUTBOUND 有可用结存 */
    DrugOptionListResult options(String purpose, String keyword);

    /** API-M36 药品档案详情 */
    DrugDetailVO detail(Long id);

    /** API-M37 新增药品档案（目录为禁用药 42203、本场已建档 40905） */
    DrugDetailVO create(DrugSaveRequest request);

    /** API-M38 修改药品档案；有流水后类别、名称、单位与包装不可改 */
    DrugDetailVO update(Long id, DrugSaveRequest request);
}
