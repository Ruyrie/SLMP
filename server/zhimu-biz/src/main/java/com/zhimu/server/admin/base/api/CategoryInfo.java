package com.zhimu.server.admin.base.api;

/**
 * 品类简要信息（t_category，管理端-03）。品类隶属畜种 species，决定计数单位 count_unit。
 */
public record CategoryInfo(Long id, String name, String species, String speciesText, String countUnit,
                           String countUnitText, Boolean isBreeding, boolean enabled) {
}