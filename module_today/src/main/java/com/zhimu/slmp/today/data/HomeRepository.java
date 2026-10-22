package com.zhimu.slmp.today.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.today.model.vo.HomeVO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;

/**
 * 首页聚合（API-T01）：成功结果按 farm_id 写内存缓存并记取数时刻，网络不稳定时显示缓存。
 */
@FeatureScope
public class HomeRepository {

    /** 缓存的首页与取数时刻 */
    public static final class Cached {
        @NonNull
        public final HomeVO home;
        public final long fetchedAt;

        Cached(@NonNull HomeVO home, long fetchedAt) {
            this.home = home;
            this.fetchedAt = fetchedAt;
        }
    }

    private final TodayApi api;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    @Inject
    public HomeRepository(@NonNull TodayApi api) {
        this.api = api;
    }

    /** API-T01 */
    @NonNull
    public Single<HomeVO> getHome() {
        return api.getHome().compose(RxResult.unwrap())
                .doOnSuccess(home -> {
                    long farmId = home.getFarm() == null ? 0 : home.getFarm().getFarmId();
                    cache.put(farmId, new Cached(home, System.currentTimeMillis()));
                });
    }

    /** 本地内存缓存（可空） */
    @Nullable
    public HomeVO getCachedHome(long farmId) {
        Cached c = cache.get(farmId);
        return c == null ? null : c.home;
    }

    @Nullable
    public Cached getCached(long farmId) {
        return cache.get(farmId);
    }

    public void clear() {
        cache.clear();
    }
}
