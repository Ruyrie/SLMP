package com.zhimu.slmp.base.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.zhimu.slmp.base.db.entity.UploadTaskEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/** 上传队列 */
@Dao
public interface UploadTaskDao {

    @Insert
    Single<Long> insert(UploadTaskEntity task);

    @Update
    Completable update(UploadTaskEntity task);

    @Query("SELECT * FROM upload_task WHERE id = :id")
    Maybe<UploadTaskEntity> get(long id);

    /** 某页面的上传项（按加入顺序），数据变化时重新发射 */
    @Query("SELECT * FROM upload_task WHERE page_code = :pageCode AND farm_id = :farmId ORDER BY id")
    Flowable<List<UploadTaskEntity>> observeByPage(String pageCode, long farmId);

    @Query("SELECT * FROM upload_task WHERE status IN ('WAITING', 'COMPRESSING', 'UPLOADING') ORDER BY id")
    Single<List<UploadTaskEntity>> unfinished();

    @Query("UPDATE upload_task SET status = :status, progress = :progress WHERE id = :id")
    Completable updateProgress(long id, String status, int progress);

    @Query("DELETE FROM upload_task WHERE id = :id")
    Completable delete(long id);

    @Query("DELETE FROM upload_task WHERE page_code = :pageCode AND farm_id = :farmId")
    Completable deleteByPage(String pageCode, long farmId);

    @Query("DELETE FROM upload_task")
    Completable clear();
}
