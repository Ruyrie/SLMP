package com.zhimu.slmp.base.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * 字典与品类品种缓存（dict_cache，API-U09、U10）：1 小时内直接使用缓存。
 * 品类品种树的 dict_type 为 CATEGORY；不按品类过滤时 category_id 为 0。
 */
@Entity(tableName = "dict_cache", primaryKeys = {"dict_type", "category_id"})
public class DictCacheEntity {

    public static final String TYPE_CATEGORY = "CATEGORY";

    @NonNull
    @ColumnInfo(name = "dict_type")
    public String dictType = "";

    @ColumnInfo(name = "category_id")
    public long categoryId;

    /** 接口 data 的 JSON */
    @NonNull
    @ColumnInfo(name = "json")
    public String json = "";

    /** 毫秒时间戳 */
    @ColumnInfo(name = "synced_at")
    public long syncedAt;
}
