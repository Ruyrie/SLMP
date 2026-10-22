package com.zhimu.slmp.common.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.DictCacheDao;
import com.zhimu.slmp.base.db.entity.DictCacheEntity;
import com.zhimu.slmp.common.model.vo.CategoryVO;
import com.zhimu.slmp.common.model.vo.DictItemVO;
import com.zhimu.slmp.common.model.vo.RecordLogVO;
import com.zhimu.slmp.network.RxResult;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 字典与品类品种（API-U09、U10，dict_cache 缓存 1 小时）与修改记录（API-U14）。
 * 缓存过期后请求失败时退回旧缓存；全部在 io 线程执行。
 */
@Singleton
public class BaseDataRepository {

    public static final long CACHE_TTL_MS = TimeUnit.HOURS.toMillis(1);

    private static final Type CATEGORY_LIST = new TypeToken<List<CategoryVO>>() { }.getType();
    private static final Type DICT_LIST = new TypeToken<List<DictItemVO>>() { }.getType();

    private final BaseDataApi api;
    private final DictCacheDao dao;
    private final Gson gson;

    @Inject
    public BaseDataRepository(@NonNull BaseDataApi api, @NonNull AppDatabase database, @NonNull Gson gson) {
        this.api = api;
        this.dao = database.dictCacheDao();
        this.gson = gson;
    }

    /** 启用的品类与品种树 */
    @NonNull
    public Single<List<CategoryVO>> getCategories() {
        return cached(DictCacheEntity.TYPE_CATEGORY, 0, CATEGORY_LIST, RxResult.unwrap(api.getCategories()));
    }

    /** 字典项；categoryId 为 null 时取全部启用项 */
    @NonNull
    public Single<List<DictItemVO>> getDicts(@NonNull String dictType, @Nullable Long categoryId) {
        return cached(dictType, categoryId == null ? 0 : categoryId, DICT_LIST,
                RxResult.unwrap(api.getDicts(dictType, null, categoryId)));
    }

    /** 修改记录（不缓存） */
    @NonNull
    public Single<List<RecordLogVO>> getRecordLogs(@NonNull String bizType, long bizId) {
        return RxResult.unwrap(api.getRecordLogs(bizType, bizId)).subscribeOn(Schedulers.io());
    }

    /** 清除字典缓存（下次重新请求） */
    @NonNull
    public Completable clearCache() {
        return dao.clear().subscribeOn(Schedulers.io());
    }

    private <T> Single<List<T>> cached(String type, long categoryId, Type listType, Single<List<T>> remote) {
        Single<List<T>> fetchAndSave = remote.flatMap(list -> {
            DictCacheEntity e = new DictCacheEntity();
            e.dictType = type;
            e.categoryId = categoryId;
            e.json = gson.toJson(list);
            e.syncedAt = System.currentTimeMillis();
            return dao.save(e).andThen(Single.just(list));
        });
        return dao.get(type, categoryId)
                .flatMapSingle(e -> System.currentTimeMillis() - e.syncedAt < CACHE_TTL_MS
                        ? Single.just(this.<T>parse(e.json, listType))
                        : fetchAndSave.onErrorReturnItem(this.<T>parse(e.json, listType)))
                .switchIfEmpty(fetchAndSave)
                .subscribeOn(Schedulers.io());
    }

    private <T> List<T> parse(String json, Type type) {
        return gson.fromJson(json, type);
    }
}
