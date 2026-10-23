package com.zhimu.server.biz.stock.service;

import com.zhimu.server.biz.stock.vo.RequestStatusVO;

/** 按请求编号查询提交结果（API-M42）；request_id 即提交时的 Idempotency-Key。 */
public interface StockRequestService {

    /** DONE（带 biz_type / biz_id）/ PROCESSING / NOT_FOUND */
    RequestStatusVO status(String requestId);
}
