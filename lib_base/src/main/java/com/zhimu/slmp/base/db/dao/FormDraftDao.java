package com.zhimu.slmp.base.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.zhimu.slmp.base.db.entity.FormDraftEntity;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

/** 表单草稿 */
@Dao
public interface FormDraftDao {

    @Query("SELECT * FROM form_draft WHERE page_code = :pageCode AND farm_id = :farmId AND biz_key = :bizKey LIMIT 1")
    Maybe<FormDraftEntity> get(String pageCode, long farmId, String bizKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable save(FormDraftEntity draft);

    @Query("DELETE FROM form_draft WHERE page_code = :pageCode AND farm_id = :farmId AND biz_key = :bizKey")
    Completable delete(String pageCode, long farmId, String bizKey);

    /** 清理过期草稿（保留 7 天） */
    @Query("DELETE FROM form_draft WHERE updated_at < :before")
    Completable deleteOlderThan(long before);

    @Query("DELETE FROM form_draft")
    Completable clear();
}
