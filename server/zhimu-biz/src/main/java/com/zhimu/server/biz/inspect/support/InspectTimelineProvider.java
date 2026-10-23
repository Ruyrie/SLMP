package com.zhimu.server.biz.inspect.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.herd.api.BatchTimelineProvider;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.biz.inspect.entity.Inspection;
import com.zhimu.server.biz.inspect.entity.InspectionItemResult;
import com.zhimu.server.biz.inspect.mapper.InspectionItemResultMapper;
import com.zhimu.server.biz.inspect.mapper.InspectionMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 批次时间线的模块07 部分（API-L16 的 INSPECT 记录）：已登记（DONE）的巡检按登记时间倒序返回，
 * 与 App mock timeline_3001.json 的 INSPECT 项一致 —— record_type = INSPECT、biz_type = INSPECTION、
 * title“巡检 · 3 项正常”、subtitle“精神状态、采食、粪便 · 李明 · 现场”、route = I04。
 * <p>
 * 模块04 读取 t_batch_timeline 后合并本实现；不返回金额字段。
 */
@Component
@RequiredArgsConstructor
public class InspectTimelineProvider implements BatchTimelineProvider {

    /** 单个批次最多带回的巡检条数（时间线分页 20，取最近 50 条足够） */
    private static final int LIMIT = 50;
    private static final String ROUTE = "I04";

    private final InspectionMapper inspectionMapper;
    private final InspectionItemResultMapper itemResultMapper;
    private final InspectSupport support;

    @Override
    public List<TimelineItemVO> extraRecords(Long farmId, Long batchId) {
        if (farmId == null || batchId == null) {
            return List.of();
        }
        List<Inspection> rows = inspectionMapper.selectList(Wrappers.<Inspection>lambdaQuery()
                .eq(Inspection::getFarmId, farmId)
                .eq(Inspection::getBatchId, batchId)
                .eq(Inspection::getInspectStatus, "DONE")
                .orderByDesc(Inspection::getInspectDate)
                .orderByDesc(Inspection::getId)
                .last("LIMIT " + LIMIT));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<TimelineItemVO> items = new ArrayList<>(rows.size());
        for (Inspection inspection : rows) {
            items.add(toItem(inspection));
        }
        return items;
    }

    private TimelineItemVO toItem(Inspection inspection) {
        List<String> names = new ArrayList<>();
        List<String> abnormal = new ArrayList<>();
        List<InspectionItemResult> results = itemResultMapper.selectByInspectionId(inspection.getId());
        for (InspectionItemResult result : results == null ? List.<InspectionItemResult>of() : results) {
            names.add(result.getItemName());
            if (Boolean.TRUE.equals(result.getOptionAbnormal())) {
                abnormal.add(result.getItemName() + " " + result.getOptionName());
            }
        }
        String registrant = support.userName(inspection.getRegistrantId());
        String location = InspectRules.locationText(inspection.getLocationStatus());
        boolean isAbnormal = Boolean.TRUE.equals(inspection.getIsAbnormal());

        TimelineItemVO item = new TimelineItemVO();
        item.setId(inspection.getId());
        item.setBizId(inspection.getId());
        item.setRecordType(TimelineItemVO.TYPE_INSPECT);
        item.setBizType("INSPECTION");
        item.setBizDate(BeijingTime.format(inspection.getInspectDate()));
        item.setOccurredAt(BeijingTime.format(inspection.getRegisteredAt()));
        if (isAbnormal) {
            String summary = !abnormal.isEmpty() ? String.join("、", abnormal)
                    : blankToNull(inspection.getAbnormalSummary());
            item.setTitle(summary == null ? "巡检 · 异常" : "巡检 · 异常 · " + summary);
        } else {
            item.setTitle("巡检 · " + names.size() + " 项正常");
        }
        item.setSubtitle(join(names.isEmpty() ? null : String.join("、", names), registrant, location));
        item.setOperatorName(registrant);
        item.setIsToday(inspection.getInspectDate() != null
                && inspection.getInspectDate().equals(BeijingTime.today()));
        item.setExpandable(false);
        item.setAction(TimelineItemVO.ACTION_NONE);
        item.setRoute(ROUTE);
        return item;
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

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
