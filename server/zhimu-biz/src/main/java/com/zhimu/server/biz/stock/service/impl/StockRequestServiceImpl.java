package com.zhimu.server.biz.stock.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.entity.StockLoss;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
import com.zhimu.server.biz.stock.mapper.StockLossMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
import com.zhimu.server.biz.stock.service.StockRequestService;
import com.zhimu.server.biz.stock.vo.RequestStatusVO;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.framework.idempotent.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 提交结果查询（API-M42）：先按 client_request_id 查四类单据（幂等键同时落库，唯一索引兜底），
 * 查不到再看 Redis 幂等记录（处理中返回 PROCESSING，已成功但单据未落库时也返回 DONE）；都没有为 NOT_FOUND。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRequestServiceImpl implements StockRequestService {

    private final StockInboundMapper inboundMapper;
    private final StockOutboundMapper outboundMapper;
    private final StockLossMapper lossMapper;
    private final StocktakeMapper stocktakeMapper;
    private final IdempotencyService idempotencyService;

    @Override
    public RequestStatusVO status(String requestId) {
        RequestStatusVO vo = new RequestStatusVO();
        if (!StringUtils.hasText(requestId)) {
            vo.setStatus("NOT_FOUND");
            return vo;
        }
        String id = requestId.trim();
        StockInbound inbound = inboundMapper.selectOne(Wrappers.<StockInbound>lambdaQuery()
                .eq(StockInbound::getClientRequestId, id)
                .orderByDesc(StockInbound::getId).last("LIMIT 1"));
        if (inbound != null) {
            vo.setStatus("DONE");
            vo.setBizType(StockCodes.BIZ_INBOUND);
            vo.setBizId(inbound.getId());
            return vo;
        }
        StockOutbound outbound = outboundMapper.selectOne(Wrappers.<StockOutbound>lambdaQuery()
                .eq(StockOutbound::getClientRequestId, id)
                .orderByDesc(StockOutbound::getId).last("LIMIT 1"));
        if (outbound != null) {
            vo.setStatus("DONE");
            vo.setBizType(StockCodes.BIZ_OUTBOUND);
            vo.setBizId(outbound.getId());
            return vo;
        }
        StockLoss loss = lossMapper.selectOne(Wrappers.<StockLoss>lambdaQuery()
                .eq(StockLoss::getClientRequestId, id)
                .orderByDesc(StockLoss::getId).last("LIMIT 1"));
        if (loss != null) {
            vo.setStatus("DONE");
            vo.setBizType(StockCodes.BIZ_LOSS);
            vo.setBizId(loss.getId());
            return vo;
        }
        Stocktake stocktake = stocktakeMapper.selectOne(Wrappers.<Stocktake>lambdaQuery()
                .eq(Stocktake::getClientRequestId, id)
                .orderByDesc(Stocktake::getId).last("LIMIT 1"));
        if (stocktake != null) {
            vo.setStatus("DONE");
            vo.setBizType(StockCodes.BIZ_STOCKTAKE);
            vo.setBizId(stocktake.getId());
            return vo;
        }
        try {
            IdempotencyService.IdempotencyResult result =
                    idempotencyService.findResult(UserContext.userId(), id);
            if (result != null) {
                vo.setStatus(result.done() ? "DONE" : "PROCESSING");
                return vo;
            }
        } catch (RuntimeException e) {
            // Redis 不可用时按“查无记录”处理，不影响主流程（App 提示“提交没有成功，请重试”）
            log.warn("查询幂等记录失败 request_id={} {}", id, e.getMessage());
        }
        vo.setStatus("NOT_FOUND");
        return vo;
    }
}
