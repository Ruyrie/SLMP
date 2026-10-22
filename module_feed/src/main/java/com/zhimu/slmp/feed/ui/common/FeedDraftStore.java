package com.zhimu.slmp.feed.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.network.GsonFactory;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * G02、G05 登记表单草稿（lib_base 的 Room form_draft）：按 page_code + farm_id + biz_key 存取，保留 7 天；
 * json 为请求字段与待确认的幂等键。全部在 io 线程执行。
 */
public final class FeedDraftStore {

    public static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    private final FormDraftDao dao;
    private final Gson gson = GsonFactory.get();

    public FeedDraftStore(@NonNull FormDraftDao dao) {
        this.dao = dao;
    }

    /** 读取 7 天内的草稿 */
    @NonNull
    public <T> Maybe<T> find(@NonNull String pageCode, long farmId, @NonNull String bizKey, @NonNull Class<T> cls) {
        long now = System.currentTimeMillis();
        return dao.get(pageCode, farmId, bizKey)
                .subscribeOn(Schedulers.io())
                .filter(e -> now - e.updatedAt <= TTL_MS)
                .map(e -> gson.fromJson(e.json, cls));
    }

    @NonNull
    public Completable save(@NonNull String pageCode, long farmId, @NonNull String bizKey, @NonNull Object draft) {
        FormDraftEntity e = new FormDraftEntity();
        e.pageCode = pageCode;
        e.farmId = farmId;
        e.bizKey = bizKey;
        e.json = gson.toJson(draft);
        e.updatedAt = System.currentTimeMillis();
        return dao.save(e).subscribeOn(Schedulers.io());
    }

    @NonNull
    public Completable delete(@NonNull String pageCode, long farmId, @Nullable String bizKey) {
        return dao.delete(pageCode, farmId, bizKey == null ? "" : bizKey).subscribeOn(Schedulers.io());
    }

    /** 启动时删除 7 天前的草稿 */
    @NonNull
    public Completable deleteExpired() {
        return dao.deleteOlderThan(System.currentTimeMillis() - TTL_MS).subscribeOn(Schedulers.io());
    }
}
