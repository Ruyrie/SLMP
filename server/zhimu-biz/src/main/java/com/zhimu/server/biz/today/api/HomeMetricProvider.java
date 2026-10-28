package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.context.FarmContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 首页指标扩展点：各模块注册为 Spring Bean 后，首页自动读取其指标；没有实现时返回 0 / 空。
 * <ul>
 *     <li>存栏、批次数、栏舍占用率、栏舍数、计数单位、员工负责栏舍：模块04（HerdHomeMetricProvider）</li>
 *     <li>休药批次与可出栏日：模块08（HealthHomeMetricProvider）</li>
 *     <li>预警数、待收款不走本接口：由来源模块写入 t_todo（WARNING 类：库存预警、异常上报；
 *     RECEIVABLE：出栏单未收款），首页与 T09 列表同口径统计</li>
 * </ul>
 */
public interface HomeMetricProvider {

    /** 模块名，例如 herd、inspection、finance */
    String moduleName();

    /** 存栏数，没有返回 0 */
    default int stockCount(Long farmId) {
        return 0;
    }

    /** 在养批次数，没有返回 0 */
    default int batchCount(Long farmId) {
        return 0;
    }

    /** 栏舍数（含停用，不含已删除）；没有实现返回 null（首页按员工负责栏舍 / 有栏舍兜底） */
    default Integer penCount(Long farmId) {
        return null;
    }

    /** 栏舍占用率（0–1），没有返回 null */
    default BigDecimal penOccupancyRate(Long farmId) {
        return null;
    }

    /** 计数单位编码 HEAD / BIRD；没有返回 null（首页按畜种兜底） */
    default String countUnit(Long farmId) {
        return null;
    }

    /** 计数单位文字 头 / 只；没有返回 null */
    default String countUnitText(Long farmId) {
        return null;
    }

    /** 仍在休药期的批次数；没有返回 0 */
    default int withdrawalBatchCount(Long farmId) {
        return 0;
    }

    /** 最近一个休药结束日期；没有返回 null */
    default java.time.LocalDate nearestWithdrawalReleaseDate(Long farmId) {
        return null;
    }

    /** 负责栏舍（员工首页 my_pens 的栏舍信息由模块04 补全） */
    default List<PenMetric> pens(FarmContext.FarmMember member) {
        return List.of();
    }

    /** 栏舍内在养批次距离可出栏日的最长天数；仅返回仍在休药期的栏舍。 */
    default Map<Long, Integer> withdrawalDaysByPen(FarmContext.FarmMember member) {
        return Map.of();
    }

    /** 单个栏舍的扩展指标 */
    record PenMetric(Long penId, String penName, String penTypeText, Integer capacity, List<BatchMetric> batches) {
    }

    /** 在栏批次扩展指标 */
    record BatchMetric(Long batchId, String batchNo, String breedText, Integer dayAge, Integer headCount,
                       String penName, String stageText) {
    }
}
