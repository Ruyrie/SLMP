package com.zhimu.slmp.stock.model.vo;

import java.math.BigDecimal;
import java.util.List;

/** API-U11 兽药疫苗目录搜索（M09 名称面板，管理端-03 BaseDrugResultVO）；字段直接公开，由 Gson 按下划线命名读写。 */
public class CatalogResultVO {
    public List<Item> items;
    public BannedHit bannedHit;

    public static class Item {
        public long id;
        public String name;
        public String alias;
        public String drugCategory;
        public String spec;
        public String measureUnit;
        public String packUnit;
        public BigDecimal packSize;
    }

    public static class BannedHit {
        public Long bannedDrugId;
        public String name;
        public String message;
    }
}
