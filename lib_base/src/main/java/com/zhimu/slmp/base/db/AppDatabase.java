package com.zhimu.slmp.base.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.zhimu.slmp.base.db.dao.DictCacheDao;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.db.dao.UploadTaskDao;
import com.zhimu.slmp.base.db.entity.DictCacheEntity;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.base.db.entity.UploadTaskEntity;

/**
 * 本地数据库 zmt.db：表单草稿、上传队列、字典缓存（03 号文档 7.2 节）。
 * 由 CoreComponent 以单例提供；升级时在 build 中加入 Migration，不使用破坏性重建。
 */
@Database(entities = {FormDraftEntity.class, UploadTaskEntity.class, DictCacheEntity.class},
        version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public static final String NAME = "zmt.db";

    public abstract FormDraftDao formDraftDao();

    public abstract UploadTaskDao uploadTaskDao();

    public abstract DictCacheDao dictCacheDao();

    @NonNull
    public static AppDatabase build(@NonNull Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, NAME)
                .build();
    }
}
