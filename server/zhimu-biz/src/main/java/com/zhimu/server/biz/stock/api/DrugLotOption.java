package com.zhimu.server.biz.stock.api;

import com.zhimu.server.common.vo.QtyVO;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批号选项（模块09 的 M27，模块08 的 D03 / D04 / D08 共用）：字段与 App 的 {@code DrugLotOptionVO} 一致。
 * <p>
 * stock 为 QtyVO（记账单位 + 包装换算）；avgPrice 为均价，员工视图由接口层裁剪。
 * 已过期、已停用、禁用药批号放入 unselectable，只能报废；insufficient 表示结存不足以满足 need_qty。
 *
 * @param lotId        批号 ID
 * @param lotNo        生产批号
 * @param expireDate   有效期至
 * @param daysToExpire 剩余天数，过期为负
 * @param lotStatus    LOT_STATUS：NORMAL / NEAR_EXPIRY / EXPIRED / RECALLED / BANNED
 * @param lotStatusText 状态文字
 * @param stock        批号结存
 * @param priorityOut  是否“优先出库”
 * @param insufficient 结存不足 need_qty
 * @param canGoScrap   可登记报废
 * @param avgPrice     均价（元/记账单位）
 * @param stockVersion 结存版本
 */
public record DrugLotOption(Long lotId, String lotNo, LocalDate expireDate, Integer daysToExpire,
                            String lotStatus, String lotStatusText, QtyVO stock, boolean priorityOut,
                            boolean insufficient, boolean canGoScrap, BigDecimal avgPrice,
                            Integer stockVersion) {
}
