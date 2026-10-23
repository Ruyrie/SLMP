package com.zhimu.slmp.stock.data;

import androidx.annotation.NonNull;

import com.zhimu.slmp.base.db.AppDatabase;
import com.zhimu.slmp.base.db.dao.FormDraftDao;
import com.zhimu.slmp.base.db.entity.FormDraftEntity;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.common.farm.FarmContext;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 表单草稿（lib_base 的 Room form_draft）：page_code M03、M04、M08、M10、M11，biz_key 新增为“new”、修改为单据编号；
 * farm_id 取当前养殖场，保留 7 天。全部在 io 线程执行。
 */
@FeatureScope
public class StockDraftRepository {

    public static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    private final FormDraftDao dao;
    private final FarmContext farmContext;

    @Inject
    public StockDraftRepository(@NonNull AppDatabase database, @NonNull FarmContext farmContext) {
        this.dao = database.formDraftDao();
        this.farmContext = farmContext;
    }

    @NonNull
    public Completable saveDraft(@NonNull String pageCode, @NonNull String bizKey, @NonNull String json) {
        FormDraftEntity e = new FormDraftEntity();
        e.pageCode = pageCode;
        e.farmId = farmContext.farmIdOrZero();
        e.bizKey = bizKey;
        e.json = json;
        e.updatedAt = System.currentTimeMillis();
        return dao.save(e).subscribeOn(Schedulers.io());
    }

    /** 7 天内的草稿；没有时为空 */
    @NonNull
    public Maybe<String> getDraft(@NonNull String pageCode, @NonNull String bizKey) {
        long now = System.currentTimeMillis();
        return dao.get(pageCode, farmContext.farmIdOrZero(), bizKey)
                .subscribeOn(Schedulers.io())
                .filter(e -> now - e.updatedAt <= TTL_MS && e.json != null)
                .map(e -> e.json);
    }

    @NonNull
    public Completable deleteDraft(@NonNull String pageCode, @NonNull String bizKey) {
        return dao.delete(pageCode, farmContext.farmIdOrZero(), bizKey).subscribeOn(Schedulers.io());
    }
}
