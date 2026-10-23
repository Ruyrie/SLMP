package com.zhimu.server.admin.base.event;

/** 标准体重立即生效 / 定时生效后发布；模块06 监听后重算在养批次的称重偏离（消费者后续模块接）。 */
public record StdWeightEffectiveEvent(Long breedId, Long weightVersionId) {
}
