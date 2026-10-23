package com.zhimu.server.biz.feeding.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.feeding.api.FeedStockPort;
import com.zhimu.server.biz.feeding.entity.FeedRecord;
import com.zhimu.server.biz.feeding.entity.WeighRecord;
import com.zhimu.server.biz.feeding.mapper.FeedRecordMapper;
import com.zhimu.server.biz.feeding.mapper.WeighRecordMapper;
import com.zhimu.server.biz.herd.api.BatchTimelineProvider;
import com.zhimu.server.biz.herd.vo.TimelineItemVO;
import com.zhimu.server.common.util.BeijingTime;
import com.zhimu.server.common.vo.UserBriefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批次时间线的模块06 扩展（API-L16 的 FEED / WEIGH 两类记录，对齐 App Mock
 * module_herd/src/debug/assets/mock/herd/timeline_3001.json）：
 * <ul>
 *     <li>投喂：title = 投喂 · 早餐 158 千克；subtitle = 饲料 · 计划 160 千克 · 执行人；
 *         expandable = true（展开显示登记人 / 登记时间 / 备注），没有 route</li>
 *     <li>称重：title = 称重 · 平均 60.0 千克；subtitle = 抽样 20 头 · 登记人；
 *         route = G06（跳称重曲线页）</li>
 * </ul>
 * 只读 t_feed_record / t_weigh_record，不返回金额字段（模块04 文档 2.3.5）。
 */
@Component
@RequiredArgsConstructor
public class FeedingTimelineProvider implements BatchTimelineProvider {

    public static final String BIZ_FEED_RECORD = "FEED_RECORD";
    public static final String BIZ_WEIGH_RECORD = "WEIGH_RECORD";
    /** 称重曲线页（App 页面编号） */
    public static final String ROUTE_WEIGH = "G06";

    private static final DateTimeFormatter MONTH_DAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final FeedRecordMapper recordMapper;
    private final WeighRecordMapper weighRecordMapper;
    private final FeedLookup lookup;

    @Override
    public List<TimelineItemVO> extraRecords(Long farmId, Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        List<TimelineItemVO> items = new ArrayList<>();
        items.addAll(feedRecords(batchId));
        items.addAll(weighRecords(batchId));
        return items;
    }

    private List<TimelineItemVO> feedRecords(Long batchId) {
        List<FeedRecord> records = recordMapper.selectList(Wrappers.<FeedRecord>lambdaQuery()
                .eq(FeedRecord::getBatchId, batchId)
                .eq(FeedRecord::getStatus, "NORMAL")
                .orderByDesc(FeedRecord::getFeedDate)
                .orderByDesc(FeedRecord::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (FeedRecord record : records) {
            userIds.add(record.getCreatedBy());
            userIds.add(record.getExecutorId());
        }
        userIds.remove(null);
        Map<Long, UserBriefVO> members = lookup.memberBriefs(userIds);
        List<TimelineItemVO> items = new ArrayList<>();
        for (FeedRecord record : records) {
            FeedStockPort.FeedBrief feed = feed(record.getFeedId());
            String recorderName = name(members, record.getCreatedBy());
            String executorName = name(members, record.getExecutorId());
            TimelineItemVO item = new TimelineItemVO();
            item.setId(record.getId());
            item.setBizId(record.getId());
            item.setRecordType(TimelineItemVO.TYPE_FEED);
            item.setBizType(BIZ_FEED_RECORD);
            item.setBizDate(BeijingTime.format(record.getFeedDate()));
            item.setOccurredAt(BeijingTime.format(record.getCreatedAt()));
            item.setTitle("投喂 · " + text(record.getMealName(), "") + " " + qty(record.getActualQty())
                    + " 千克");
            item.setSubtitle((feed == null ? "" : feed.feedName() + " · ")
                    + "计划 " + qty(record.getPlanQty()) + " 千克"
                    + (executorName == null ? "" : " · " + executorName));
            item.setOperatorName(recorderName);
            item.setIsToday(BeijingTime.today().equals(record.getFeedDate()));
            List<TimelineItemVO.KeyValue> detail = new ArrayList<>();
            detail.add(kv("登记人", recorderName));
            detail.add(kv("登记时间", occurredText(record.getCreatedAt())));
            if (record.getRemark() != null && !record.getRemark().isBlank()) {
                detail.add(kv("备注", record.getRemark()));
            }
            item.setDetail(detail);
            item.setExpandable(true);
            item.setAction(TimelineItemVO.ACTION_NONE);
            items.add(item);
        }
        return items;
    }

    private List<TimelineItemVO> weighRecords(Long batchId) {
        List<WeighRecord> records = weighRecordMapper.selectList(Wrappers.<WeighRecord>lambdaQuery()
                .eq(WeighRecord::getBatchId, batchId)
                .orderByDesc(WeighRecord::getWeighDate)
                .orderByDesc(WeighRecord::getId));
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (WeighRecord record : records) {
            userIds.add(record.getCreatedBy());
        }
        userIds.remove(null);
        Map<Long, UserBriefVO> members = lookup.memberBriefs(userIds);
        List<TimelineItemVO> items = new ArrayList<>();
        for (WeighRecord record : records) {
            String recorderName = name(members, record.getCreatedBy());
            boolean individual = "INDIVIDUAL".equals(record.getMode());
            TimelineItemVO item = new TimelineItemVO();
            item.setId(record.getId());
            item.setBizId(record.getId());
            item.setRecordType(TimelineItemVO.TYPE_WEIGH);
            item.setBizType(BIZ_WEIGH_RECORD);
            item.setBizDate(BeijingTime.format(record.getWeighDate()));
            item.setOccurredAt(BeijingTime.format(record.getCreatedAt()));
            item.setTitle("称重 · 平均 " + oneDecimal(record.getAvgWeight()) + " 千克");
            item.setSubtitle((individual ? "逐头 " : "抽样 ")
                    + (record.getSampleCount() == null ? 0 : record.getSampleCount()) + " 头"
                    + (recorderName == null ? "" : " · " + recorderName));
            item.setOperatorName(recorderName);
            item.setIsToday(BeijingTime.today().equals(record.getWeighDate()));
            item.setExpandable(false);
            item.setAction(TimelineItemVO.ACTION_NONE);
            item.setRoute(ROUTE_WEIGH);
            items.add(item);
        }
        return items;
    }

    private FeedStockPort.FeedBrief feed(Long feedId) {
        return feedId == null ? null : lookup.feed(feedId);
    }

    private static String name(Map<Long, UserBriefVO> members, Long userId) {
        UserBriefVO brief = userId == null ? null : members.get(userId);
        return brief == null ? null : brief.getName();
    }

    private static TimelineItemVO.KeyValue kv(String key, String value) {
        TimelineItemVO.KeyValue kv = new TimelineItemVO.KeyValue();
        kv.setKey(key);
        kv.setValue(value);
        return kv;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String qty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static String oneDecimal(BigDecimal value) {
        return value == null ? "0.0" : value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private static String occurredText(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        LocalDate today = BeijingTime.today();
        if (today.equals(time.toLocalDate())) {
            return "今天 " + time.format(HOUR_MINUTE);
        }
        return time.format(MONTH_DAY_TIME);
    }
}
