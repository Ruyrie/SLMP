package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.query.FinanceQueries;
import com.zhimu.server.biz.finance.vo.SaleAdviceVos;
import com.zhimu.server.common.api.PageVO;

/** AI 出栏建议（API-E33–E36）。 */
public interface SaleAdviceService {

    SaleAdviceVos.SaleAdviceVO latest(Long batchId);

    SaleAdviceVos.SaleAdviceVO regenerate(Long batchId);

    PageVO<SaleAdviceVos.SaleAdviceVO> history(FinanceQueries.SaleAdviceQuery query);

    SaleAdviceVos.SaleAdviceVO get(Long id);
}
