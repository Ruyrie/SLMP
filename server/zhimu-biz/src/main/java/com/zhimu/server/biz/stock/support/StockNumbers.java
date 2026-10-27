package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.framework.mybatis.TenantSkip;
import com.zhimu.server.biz.stock.entity.StockInbound;
import com.zhimu.server.biz.stock.entity.StockOutbound;
import com.zhimu.server.biz.stock.entity.Stocktake;
import com.zhimu.server.biz.stock.mapper.StockInboundMapper;
import com.zhimu.server.biz.stock.mapper.StockOutboundMapper;
import com.zhimu.server.biz.stock.mapper.StocktakeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * 单号生成：RK（入库）、CK（出库）、PD（盘点）+ yyMMdd + 4 位当日流水，例如 RK2610190001。
 * <p>
 * 取当日最大单号 +1；若已被占用（并发或历史空洞）继续 +1。单号列有唯一索引兜底，
 * 并发下极小概率的 DuplicateKey 由调用方整单回滚，App 重试即可。
 */
@Component
@RequiredArgsConstructor
public class StockNumbers {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MAX_TRY = 50;

    private final StockInboundMapper inboundMapper;
    private final StockOutboundMapper outboundMapper;
    private final StocktakeMapper stocktakeMapper;

    public String nextInboundNo(LocalDate date) {
        // 单号在数据库全局唯一，分配时必须检查所有养殖场；不返回其他场业务数据。
        return TenantSkip.call(() -> {
            String prefix = "RK" + date.format(DAY);
            StockInbound last = inboundMapper.selectOne(Wrappers.<StockInbound>lambdaQuery()
                    .likeRight(StockInbound::getInboundNo, prefix)
                    .orderByDesc(StockInbound::getInboundNo).last("LIMIT 1"));
            return allocate(prefix, last == null ? null : last.getInboundNo(),
                    no -> inboundMapper.selectCount(Wrappers.<StockInbound>lambdaQuery()
                            .eq(StockInbound::getInboundNo, no)));
        });
    }

    public String nextOutboundNo(LocalDate date) {
        // 单号在数据库全局唯一，分配时必须检查所有养殖场；不返回其他场业务数据。
        return TenantSkip.call(() -> {
            String prefix = "CK" + date.format(DAY);
            StockOutbound last = outboundMapper.selectOne(Wrappers.<StockOutbound>lambdaQuery()
                    .likeRight(StockOutbound::getOutboundNo, prefix)
                    .orderByDesc(StockOutbound::getOutboundNo).last("LIMIT 1"));
            return allocate(prefix, last == null ? null : last.getOutboundNo(),
                    no -> outboundMapper.selectCount(Wrappers.<StockOutbound>lambdaQuery()
                            .eq(StockOutbound::getOutboundNo, no)));
        });
    }

    public String nextStocktakeNo(LocalDate date) {
        // 单号在数据库全局唯一，分配时必须检查所有养殖场；不返回其他场业务数据。
        return TenantSkip.call(() -> {
            String prefix = "PD" + date.format(DAY);
            Stocktake last = stocktakeMapper.selectOne(Wrappers.<Stocktake>lambdaQuery()
                    .likeRight(Stocktake::getStocktakeNo, prefix)
                    .orderByDesc(Stocktake::getStocktakeNo).last("LIMIT 1"));
            return allocate(prefix, last == null ? null : last.getStocktakeNo(),
                    no -> stocktakeMapper.selectCount(Wrappers.<Stocktake>lambdaQuery()
                            .eq(Stocktake::getStocktakeNo, no)));
        });
    }

    private static String allocate(String prefix, String maxNo, Function<String, Long> exists) {
        int start = nextSeq(maxNo);
        for (int seq = start; seq < start + MAX_TRY; seq++) {
            String no = prefix + String.format("%04d", seq);
            Long count = exists.apply(no);
            if (count == null || count == 0) {
                return no;
            }
        }
        return prefix + String.format("%04d", start + MAX_TRY);
    }

    private static int nextSeq(String maxNo) {
        if (maxNo == null || maxNo.length() < 4) {
            return 1;
        }
        try {
            return Integer.parseInt(maxNo.substring(maxNo.length() - 4)) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
