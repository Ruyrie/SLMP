package com.zhimu.slmp.health.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.network.GsonFactory;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 登记表单草稿（Room form_draft，D04、D08 新建状态）：按 page_code、farm_id、biz_key（batch_id、pen_id 或 task_id）隔离，
 * 保留 7 天；页面 onStop 写入，再次进入时询问“恢复”；提交成功或“放弃”时删除。
 */
public final class DraftStore<T> {

    public static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    private final FormDraftDao dao;
    private final String pageCode;
    private final Class<T> type;
    private final CompositeDisposable disposables;
    private final Gson gson = GsonFactory.get();

    public DraftStore(@NonNull FormDraftDao dao, @NonNull String pageCode, @NonNull Class<T> type,
                      @NonNull CompositeDisposable disposables) {
        this.dao = dao;
        this.pageCode = pageCode;
        this.type = type;
        this.disposables = disposables;
    }

    public void save(long farmId, @NonNull String bizKey, @NonNull T draft) {
        FormDraftEntity e = new FormDraftEntity();
        e.pageCode = pageCode;
        e.farmId = farmId;
        e.bizKey = bizKey;
        e.json = gson.toJson(draft);
        e.updatedAt = System.currentTimeMillis();
        disposables.add(dao.save(e).subscribeOn(Schedulers.io()).subscribe(() -> {
        }, t -> {
        }));
    }

    /** 读取 7 天内的草稿（主线程回调）；过期的删除 */
    public void load(long farmId, @NonNull String bizKey, @NonNull Consumer<T> onFound) {
        disposables.add(dao.get(pageCode, farmId, bizKey).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> {
                    if (System.currentTimeMillis() - e.updatedAt > TTL_MS) {
                        delete(farmId, bizKey);
                        return;
                    }
                    T d = parse(e.json);
                    if (d != null) {
                        onFound.accept(d);
                    }
                }, t -> {
                }));
    }

    public void delete(long farmId, @NonNull String bizKey) {
        disposables.add(dao.delete(pageCode, farmId, bizKey).subscribeOn(Schedulers.io()).subscribe(() -> {
        }, t -> {
        }));
    }

    @Nullable
    private T parse(@Nullable String json) {
        try {
            return json == null ? null : gson.fromJson(json, type);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
