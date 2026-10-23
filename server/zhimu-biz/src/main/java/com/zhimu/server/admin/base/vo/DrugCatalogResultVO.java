package com.zhimu.server.admin.base.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * API-U11 响应 data：items + banned_hit，字段与 App module_stock CatalogResultVO 一致。
 * 关键词命中禁用药时 items 为空、banned_hit 有值。
 */
@Getter
@Setter
public class DrugCatalogResultVO {

    private List<DrugCatalogItemVO> items = new ArrayList<>();
    private BannedDrugHitVO bannedHit;
}
