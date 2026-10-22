package com.zhimu.slmp.base.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.zhimu.slmp.base.db.entity.DictCacheEntity;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

/** 字典缓存 */
@Dao
public interface DictCacheDao {

    @Query("SELECT * FROM dict_cache WHERE dict_type = :dictType AND category_id = :categoryId LIMIT 1")
    Maybe<DictCacheEntity> get(String dictType, long categoryId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable save(DictCacheEntity cache);

    @Query("DELETE FROM dict_cache")
    Completable clear();
}
