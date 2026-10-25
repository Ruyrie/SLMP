package com.zhimu.server.biz.stock.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.BatchTimelineProvider;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.stock.entity.Drug;
import com.zhimu.server.biz.stock.entity.DrugLot;
import com.zhimu.server.biz.stock.entity.StockFlow;
import com.zhimu.server.biz.stock.enums.StockCodes;
import com.zhimu.server.biz.stock.mapper.DrugLotMapper;
import com.zhimu.server.biz.stock.mapper.DrugMapper;
import com.zhimu.server.biz.stock.mapper.StockFlowMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次时间线的模块09 部分（API-L16 的 IMMUNE / DRUG 记录）：该批次药品出库流水（接种 / 用药 / 消毒 / 领用）
 * 按发生时间倒序返回，与 App mock timeline_3001_immune.json / timeline_3001_drug.json 一致 ——
 * 接种 record_type = IMMUNE、title“免疫 · 口蹄疫灭活疫苗”；其余 record_type = DRUG、title“用药 · 氟苯尼考粉”；
 * biz_type = 来源记录类型、subtitle 带批号与数量、route 按类型 D01 免疫 / D05 用药 / D08 消毒。
 * <p>
 * 模块08 合并后可由其自带的记录接管更细的字段（休药期等）；本实现只读流水，不返回金额字段。
 */
@Component
@RequiredArgsConstructor
public class StockTimelineProvider implements BatchTimelineProvider {

    /** 单个批次最多带回的药品流水条数 */
    private static final int LIMIT = 50;
    private static final String ROUTE_IMMUNE = "D01";
    private static final String ROUTE_MEDICATION = "D05";
    private static final String ROUTE_DISINFECT = "D08";
    private static final List<String> DRUG_USE_FLOWS = List.of(StockCodes.FLOW_VACCINE_USE,
            StockCodes.FLOW_MEDICATION_USE, StockCodes.FLOW_DISINFECT_USE, StockCodes.FLOW_MANUAL_USE);

    private final StockFlowMapper flowMapper;
    private final DrugMapper drugMapper;
    private final DrugLotMapper drugLotMapper;
    private final StockLookups lookups;

    @Override
    public List<TimelineItemVO> extraRecords(Long farmId, Long batchId) {
        if (farmId == null || batchId == null) {
            return List.of();
        }
        List<StockFlow> flows = flowMapper.selectList(Wrappers.<StockFlow>lambdaQuery()
                .eq(StockFlow::getFarmId, farmId)
                .eq(StockFlow::getBatchId, batchId)
                .eq(StockFlow::getMaterialType, StockCodes.MATERIAL_DRUG)
                .eq(StockFlow::getDirection, StockCodes.DIRECTION_OUT)
                .eq(StockFlow::getStatus, StockCodes.STATUS_NORMAL)
                .in(StockFlow::getFlowType, DRUG_USE_FLOWS)
                .orderByDesc(StockFlow::getOccurAt)
                .orderByDesc(StockFlow::getId)
                .last("LIMIT " + LIMIT));
        if (flows == null || flows.isEmpty()) {
            return List.of();
        }
        Map<Long, Drug> drugs = new HashMap<>();
        Map<Long, DrugLot> lots = new HashMap<>();
        List<TimelineItemVO> items = new ArrayList<>(flows.size());
        for (StockFlow flow : flows) {
            items.add(toItem(flow, drugs, lots));
        }
        return items;
    }

    private TimelineItemVO toItem(StockFlow flow, Map<Long, Drug> drugs, Map<Long, DrugLot> lots) {
        Drug drug = flow.getDrugId() == null ? null
                : drugs.computeIfAbsent(flow.getDrugId(), drugMapper::selectById);
        DrugLot lot = flow.getDrugLotId() == null ? null
                : lots.computeIfAbsent(flow.getDrugLotId(), drugLotMapper::selectById);
        String drugName = drug == null ? "药品" : drug.getName();
        String title = actionText(flow.getFlowType()) + " · " + drugName;
        String unitText = drug == null ? "" : StockCodes.unitText(drug.getUnit());
        String qtyText = flow.getQty() == null ? null
                : StockQty.plain(flow.getQty()) + (StringUtils.hasText(unitText) ? " " + unitText : "");
        String lotText = lot == null || !StringUtils.hasText(lot.getLotNo()) ? null
                : "批号 " + lot.getLotNo();
        String operator = flow.getOperatorId() == null ? null : lookups.userName(flow.getOperatorId());

        TimelineItemVO item = new TimelineItemVO();
        item.setId(flow.getId());
        item.setBizId(flow.getBizId() == null ? flow.getId() : flow.getBizId());
        // 接种归到“免疫”筛选（App L06 的 IMMUNE 页签），其余用药、消毒、领用归“用药”
        item.setRecordType(StockCodes.FLOW_VACCINE_USE.equals(flow.getFlowType())
                ? TimelineItemVO.TYPE_IMMUNE : TimelineItemVO.TYPE_DRUG);
        item.setBizType(flow.getBizType());
        item.setBizDate(BeijingTime.format(flow.getBizDate()));
        item.setOccurredAt(BeijingTime.format(flow.getOccurAt()));
        item.setTitle(title);
        item.setSubtitle(join(lotText, qtyText, operator));
        item.setOperatorName(operator);
        item.setIsToday(flow.getBizDate() != null && flow.getBizDate().equals(BeijingTime.today()));
        item.setExpandable(false);
        item.setAction(TimelineItemVO.ACTION_NONE);
        item.setRoute(route(flow.getFlowType()));
        return item;
    }

    private static String actionText(String flowType) {
        if (StockCodes.FLOW_VACCINE_USE.equals(flowType)) {
            return "免疫";
        }
        if (StockCodes.FLOW_MEDICATION_USE.equals(flowType)) {
            return "用药";
        }
        if (StockCodes.FLOW_DISINFECT_USE.equals(flowType)) {
            return "消毒";
        }
        return "领用";
    }

    private static String route(String flowType) {
        if (StockCodes.FLOW_VACCINE_USE.equals(flowType)) {
            return ROUTE_IMMUNE;
        }
        if (StockCodes.FLOW_DISINFECT_USE.equals(flowType)) {
            return ROUTE_DISINFECT;
        }
        return ROUTE_MEDICATION;
    }

    private static String join(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                values.add(part);
            }
        }
        return String.join(" · ", values);
    }
}
