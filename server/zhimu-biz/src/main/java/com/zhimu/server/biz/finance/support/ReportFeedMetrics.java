package com.zhimu.server.biz.finance.support;

import com.zhimu.server.biz.finance.mapper.ReportFeedQueryMapper;
import com.zhimu.server.biz.finance.vo.ReportVos;
import com.zhimu.server.framework.mybatis.TenantSkip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportFeedMetrics {

    private final ReportFeedQueryMapper mapper;

    public ReportVos.Feed between(List<Long> farmIds, LocalDate start, LocalDate end,
                                  LocalDate previousStart, LocalDate previousEnd) {
        BigDecimal current = total(farmIds, start, end);
        BigDecimal previous = total(farmIds, previousStart, previousEnd);
        ReportVos.Feed feed = new ReportVos.Feed();
        feed.setKg(FinanceSupport.scale2(current));
        feed.setComparable(previous.signum() > 0);
        feed.setChangeRate(feed.getComparable() ? FinanceSupport.rate(current, previous) : null);
        return feed;
    }

    private BigDecimal total(List<Long> farmIds, LocalDate start, LocalDate end) {
        if (farmIds == null || farmIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return FinanceSupport.nz(TenantSkip.call(() -> mapper.sumActualKg(farmIds, start, end)));
    }
}
