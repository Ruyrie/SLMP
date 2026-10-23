package com.zhimu.server.biz.today.api;

import com.zhimu.server.common.context.FarmContext;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页指标扩展点：后续模块注册为 Spring Bean 后，首页自动读取其指标；没有实现时返回 0 / 空。
 * <p>
 * 本批只接模块02 与种子数据，未接入的模块标“待模块xx”：
 * <ul>
 *     <li>存栏、批次数、栏舍占用率：待模块04（栏舍/批次）</li>
 *     <li>预警数、预警名称：模块07（巡检异常、紧急诊断）待接入；本批按 TODO 表预警类待办统计</li>
 *     <li>待收款笔数/天数/金额：待模块10（出栏与收款）</li>
 *     <li>AI 推荐问：待模块07/11 接入，本批返回空</li>
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

    /** 栏舍占用率（0–1），没有返回 null */
    default BigDecimal penOccupancyRate(Long farmId) {
        return null;
    }

    /** 未解除的预警数（不含休药中批次），没有返回 0 */
    default int warningCount(Long farmId) {
        return 0;
    }

    /** 预警名称（首页展示前几个），没有返回空集合 */
    default List<String> warningNames(Long farmId) {
        return List.of();
    }

    /** 待收款已逾期笔数，没有返回 0 */
    default int receivableOverdueCount(Long farmId) {
        return 0;
    }

    /** 待收款最大逾期天数，没有返回 0 */
    default int receivableMaxOverdueDays(Long farmId) {
        return 0;
    }

    /** 待收款未收金额，没有返回 null */
    default BigDecimal receivableAmount(Long farmId) {
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

    /** 负责栏舍（员工首页 my_pens 的栏舍信息由模块04 补全） */
    default List<PenMetric> pens(FarmContext.FarmMember member) {
        return List.of();
    }

    /** 单个栏舍的扩展指标 */
    record PenMetric(Long penId, String penName, String penTypeText, Integer capacity, List<BatchMetric> batches) {
    }

    /** 在栏批次扩展指标 */
    record BatchMetric(Long batchId, String batchNo, String breedText, Integer dayAge, Integer headCount,
                       String penName, String stageText) {
    }
}
