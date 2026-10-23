package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.api.DrugLotOption;
import com.zhimu.server.biz.stock.vo.DrugLotDetailVO;
import com.zhimu.server.biz.stock.vo.DrugLotItemVO;
import com.zhimu.server.biz.stock.vo.DrugLotOptionsVO;
import com.zhimu.server.common.api.PageVO;

import java.math.BigDecimal;
import java.util.List;

/** 药品批号：批号列表 / 详情 / 可选批号（API-M25–M27），并供模块08 的端口复用。 */
public interface StockDrugLotService {

    /** API-M25 批号列表：只含结存大于 0 的批号，tab_counts 带 ATTENTION、NORMAL */
    PageVO<DrugLotItemVO> page(String group, String category, String keyword, int page, int size);

    /** API-M26 批号详情 */
    DrugLotDetailVO detail(Long id);

    /** API-M27 选择批号：已过期、已停用、禁用药进 unselectable */
    DrugLotOptionsVO options(Long drugId, String purpose, BigDecimal needQty);

    /** 供 DrugStockPort 使用的扁平批号选项（selectable 在前，unselectable 在后） */
    List<DrugLotOption> optionRecords(Long drugId, String purpose, BigDecimal needQty);
}
