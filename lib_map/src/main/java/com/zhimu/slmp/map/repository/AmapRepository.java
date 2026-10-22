package com.zhimu.slmp.map.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.help.Inputtips;
import com.amap.api.services.help.InputtipsQuery;
import com.amap.api.services.help.Tip;
import com.zhimu.slmp.common.di.FeatureScope;
import com.zhimu.slmp.map.location.OnceLocator;
import com.zhimu.slmp.map.model.vo.AddressTipVO;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.MapPoint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 高德定位、逆地理编码、输入提示（F04）。逆地理编码与输入提示用 SDK 的同步方法在 io 线程执行。
 */
@FeatureScope
public class AmapRepository {

    /** 逆地理编码半径（米） */
    public static final float REGEO_RADIUS = 200f;
    /** 输入提示最多条数 */
    public static final int MAX_TIPS = 6;

    private final Context context;

    @Inject
    public AmapRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** 单次定位（高精度、超时 10 秒） */
    @NonNull
    public Single<MapPoint> locateOnce() {
        return OnceLocator.locate(context);
    }

    /** 逆地理编码：取 formatAddress、province、city、district、township、adcode；city 为空（直辖市）用 province */
    @NonNull
    public Single<LocationResult> regeocode(@NonNull BigDecimal latitude, @NonNull BigDecimal longitude) {
        return Single.fromCallable(() -> {
            GeocodeSearch search = new GeocodeSearch(context);
            RegeocodeQuery query = new RegeocodeQuery(
                    new LatLonPoint(latitude.doubleValue(), longitude.doubleValue()), REGEO_RADIUS,
                    GeocodeSearch.AMAP);
            RegeocodeAddress a = search.getFromLocation(query);
            if (a == null || TextUtils.isEmpty(a.getAdCode())) {
                throw new IllegalStateException("没有获取到地址");
            }
            return new LocationResult(longitude, latitude, a.getProvince(), a.getCity(), a.getDistrict(),
                    emptyToNull(a.getTownship()), a.getAdCode(), a.getFormatAddress(), null, null);
        }).subscribeOn(Schedulers.io());
    }

    /** 输入提示：不限城市（city 只用于排序），过滤无坐标项，最多 6 条 */
    @NonNull
    public Single<List<AddressTipVO>> inputTips(@NonNull String keyword, @Nullable String city) {
        return Single.fromCallable(() -> {
            InputtipsQuery query = new InputtipsQuery(keyword, city == null ? "" : city);
            query.setCityLimit(false);
            Inputtips inputtips = new Inputtips(context, query);
            List<Tip> tips = inputtips.requestInputtips();
            List<AddressTipVO> list = new ArrayList<>();
            if (tips != null) {
                for (Tip t : tips) {
                    LatLonPoint p = t.getPoint();
                    if (p == null || TextUtils.isEmpty(t.getName())) {
                        continue;
                    }
                    list.add(new AddressTipVO(t.getName(), t.getDistrict(),
                            LocationResult.coord(p.getLatitude()), LocationResult.coord(p.getLongitude())));
                    if (list.size() >= MAX_TIPS) {
                        break;
                    }
                }
            }
            return list;
        }).subscribeOn(Schedulers.io());
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }
}
