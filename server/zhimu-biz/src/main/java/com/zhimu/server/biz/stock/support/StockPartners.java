package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.stock.entity.StockPartner;
import com.zhimu.server.biz.stock.mapper.StockPartnerMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 最近供应商 / 退回对象：按“养殖场 + 类型 + 名称”累计次数与最近使用时间（API-M12）。 */
@Component
@RequiredArgsConstructor
public class StockPartners {

    private final StockPartnerMapper partnerMapper;

    public void touch(Long farmId, String kind, String name) {
        if (farmId == null || !StringUtils.hasText(name)) {
            return;
        }
        String trimmed = name.trim();
        StockPartner partner = partnerMapper.selectOne(Wrappers.<StockPartner>lambdaQuery()
                .eq(StockPartner::getKind, kind)
                .eq(StockPartner::getName, trimmed)
                .last("LIMIT 1"));
        if (partner == null) {
            StockPartner created = new StockPartner();
            created.setFarmId(farmId);
            created.setKind(kind);
            created.setName(trimmed);
            created.setUseCount(1);
            created.setLastUsedAt(BeijingTime.now());
            partnerMapper.insert(created);
            return;
        }
        partner.setUseCount((partner.getUseCount() == null ? 0 : partner.getUseCount()) + 1);
        partner.setLastUsedAt(BeijingTime.now());
        partnerMapper.updateById(partner);
    }
}
