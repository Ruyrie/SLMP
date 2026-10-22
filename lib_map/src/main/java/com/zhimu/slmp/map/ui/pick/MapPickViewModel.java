package com.zhimu.slmp.map.ui.pick;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.model.LatLng;
import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.mvvm.SingleLiveEvent;
import com.zhimu.slmp.map.contract.PickLocationContract;
import com.zhimu.slmp.map.model.vo.AddressTipVO;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.MapPoint;
import com.zhimu.slmp.map.model.vo.PickLocationInput;
import com.zhimu.slmp.map.repository.AmapRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * F04 养殖场位置选点（模块02 技术文档 2.4.4、2.7.1）：大头针固定在中心，地图停止 300 ms 后逆地理编码（switchMap 丢弃旧结果），
 * 地点输入提示 300 ms 防抖，单次定位，确认回传 LocationResult。
 * 未配置高德 Key（或地图创建失败）时为手动模式：填写省、市、区县与详细地址，经纬度与 adcode 为空（source = MANUAL）。
 */
public class MapPickViewModel extends BaseViewModel {

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_LOADING = "LOADING";
    public static final String STATE_OK = "OK";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_NONE = "NONE";

    /** 地图停止、输入停止后的防抖（UI 文档 300 ms；03 号 9.2 节为 500 ms，按模块文档） */
    public static final long DEBOUNCE_MS = 300;
    public static final int ADDRESS_MAX = 200;
    public static final int REGION_MAX = 30;

    static final String DEFAULT_TITLE = "养殖场位置";
    static final String HINT_DRAG = "拖动地图调整位置";
    static final String HINT_CHANGE = "拖动地图调整位置 · 确认后需重新审核";
    static final String MSG_NO_PERMISSION = "没有定位权限，你可以在系统设置中开启，或直接搜索地址";
    static final String MSG_LOCATE_FAILED = "定位失败，你可以搜索地址或拖动地图选点";

    private final AmapRepository repository;

    @NonNull
    private PickLocationInput input = new PickLocationInput(PickLocationInput.MODE_NEW, null, null);
    private boolean initialized;
    private boolean mapAvailable;

    private final MutableLiveData<String> titleName = new MutableLiveData<>(DEFAULT_TITLE);
    private final MutableLiveData<LocationResult> result = new MutableLiveData<>();
    private final MutableLiveData<String> addressState = new MutableLiveData<>(STATE_IDLE);
    private final MutableLiveData<MapPoint> myLocation = new MutableLiveData<>();
    private final MutableLiveData<String> hintLine = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> locating = new MutableLiveData<>(false);
    private final MutableLiveData<List<AddressTipVO>> tips = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> tipsVisible = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showPermissionNotice = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> manualEditing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> mapLoadFailed = new MutableLiveData<>(false);
    private final MutableLiveData<String> manualAddress = new MutableLiveData<>("");

    /** 手动模式（未配置高德 Key） */
    private final MutableLiveData<Boolean> manualMode = new MutableLiveData<>(false);
    private final MutableLiveData<String> manualProvince = new MutableLiveData<>("");
    private final MutableLiveData<String> manualCity = new MutableLiveData<>("");
    private final MutableLiveData<String> manualDistrict = new MutableLiveData<>("");

    private final MediatorLiveData<Boolean> canConfirm = new MediatorLiveData<>(false);

    private final SingleLiveEvent<Void> requestPermission = new SingleLiveEvent<>();
    private final SingleLiveEvent<MapPoint> moveCamera = new SingleLiveEvent<>();
    private final SingleLiveEvent<Void> hideKeyboard = new SingleLiveEvent<>();
    private final SingleLiveEvent<LocationResult> finishWithResult = new SingleLiveEvent<>();

    private final PublishSubject<MapPoint> cameraIdle = PublishSubject.create();
    private final PublishSubject<String> searchText = PublishSubject.create();

    /** 下一次相机停止来自选择搜索提示 */
    private boolean fromSearch;
    /** 下一次相机停止不做逆地理编码（以原位置为中心时保留原地址） */
    private boolean skipNextIdle;
    @NonNull
    private String keyword = "";

    @Inject
    public MapPickViewModel(@NonNull AmapRepository repository) {
        this.repository = repository;
        canConfirm.addSource(result, v -> updateCanConfirm());
        canConfirm.addSource(addressState, v -> updateCanConfirm());
        canConfirm.addSource(manualMode, v -> updateCanConfirm());
        canConfirm.addSource(manualProvince, v -> updateCanConfirm());
        canConfirm.addSource(manualDistrict, v -> updateCanConfirm());
        canConfirm.addSource(manualAddress, v -> updateCanConfirm());

        disposables.add(cameraIdle
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .switchMapSingle(p -> repository.regeocode(p.getLatitude(), p.getLongitude())
                        .map(r -> new Geo(p, r))
                        .onErrorReturnItem(new Geo(p, null)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onGeocoded, t -> addressState.setValue(STATE_FAILED)));

        disposables.add(searchText
                .debounce(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
                .switchMapSingle(k -> k.isEmpty()
                        ? Single.just(Collections.<AddressTipVO>emptyList())
                        : repository.inputTips(k, cityOf(result.getValue()))
                        .onErrorReturnItem(Collections.emptyList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    tips.setValue(list);
                    tipsVisible.setValue(!keyword.isEmpty());
                }, t -> tipsVisible.setValue(false)));
    }

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        PickLocationInput in = handle.get(PickLocationContract.EXTRA_INPUT);
        if (in != null) {
            input = in;
        }
        titleName.setValue(TextUtils.isEmpty(input.getFarmName()) ? DEFAULT_TITLE : input.getFarmName());
    }

    // ===== 初始化 =====

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 进入页面时调用一次。
     *
     * @param hasPermission 已有定位权限
     * @param available     地图可用（已配置 Key、已同意隐私政策、MapView 创建成功）
     */
    public void init(boolean hasPermission, boolean available) {
        if (initialized) {
            return;
        }
        initialized = true;
        mapAvailable = available;
        manualMode.setValue(!available);
        updateHint();
        LocationResult initial = input.getInitial();
        if (!available) {
            if (initial != null) {
                manualProvince.setValue(nonNull(initial.getProvince()));
                manualCity.setValue(nonNull(initial.getCity()));
                manualDistrict.setValue(nonNull(initial.getDistrict()));
                manualAddress.setValue(nonNull(initial.getAddress()));
            }
            addressState.setValue(STATE_IDLE);
            return;
        }
        if (initial != null && initial.hasCoordinate()) {
            result.setValue(initial);
            addressState.setValue(STATE_OK);
            skipNextIdle = true;
            moveCamera.setValue(new MapPoint(initial.getLatitude(), initial.getLongitude(), null));
        } else if (hasPermission) {
            addressState.setValue(STATE_NONE);
            locate();
        } else {
            addressState.setValue(STATE_NONE);
            showPermissionNotice.setValue(true);
        }
    }

    /** 地图重建后（重新加载、配置变化）把相机移回当前结果，不重新逆地理编码 */
    public void restoreCamera() {
        LocationResult r = result.getValue();
        if (mapAvailable && r != null && r.hasCoordinate()) {
            skipNextIdle = true;
            moveCamera.setValue(new MapPoint(r.getLatitude(), r.getLongitude(), null));
        }
    }

    // ===== 拖动与逆地理编码 =====

    public void onCameraMoving() {
        if (!mapAvailable || skipNextIdle) {
            return;
        }
        addressState.setValue(STATE_LOADING);
    }

    public void onCameraIdle(double latitude, double longitude) {
        if (!mapAvailable) {
            return;
        }
        if (skipNextIdle) {
            skipNextIdle = false;
            addressState.setValue(result.getValue() != null ? STATE_OK : STATE_NONE);
            return;
        }
        addressState.setValue(STATE_LOADING);
        cameraIdle.onNext(new MapPoint(latitude, longitude, null));
    }

    private void onGeocoded(@NonNull Geo geo) {
        String source = decideSource(geo.point);
        MapPoint loc = myLocation.getValue();
        Integer accuracy = loc == null ? null : clampAccuracy(loc.getAccuracy());
        if (geo.result != null) {
            result.setValue(geo.result.withSource(source, accuracy));
            addressState.setValue(STATE_OK);
            manualEditing.setValue(false);
        } else {
            result.setValue(new LocationResult(geo.point.getLongitude(), geo.point.getLatitude(), null, null, null,
                    null, null, null, source, null).withSource(source, accuracy));
            addressState.setValue(STATE_FAILED);
        }
    }

    /** 选提示为 SEARCH；中心在定位点精度范围内为 LOCATION；其余为 DRAG */
    @NonNull
    private String decideSource(@NonNull MapPoint center) {
        if (fromSearch) {
            fromSearch = false;
            return LocationResult.SOURCE_SEARCH;
        }
        MapPoint loc = myLocation.getValue();
        if (loc != null) {
            float distance = AMapUtils.calculateLineDistance(
                    new LatLng(center.getLatitude().doubleValue(), center.getLongitude().doubleValue()),
                    new LatLng(loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue()));
            int tolerance = Math.max(loc.getAccuracy() == null ? 0 : loc.getAccuracy(), 10);
            if (distance <= tolerance) {
                return LocationResult.SOURCE_LOCATION;
            }
        }
        return LocationResult.SOURCE_DRAG;
    }

    @Nullable
    private static Integer clampAccuracy(@Nullable Integer accuracy) {
        if (accuracy == null) {
            return null;
        }
        return Math.max(0, Math.min(10000, accuracy));
    }

    // ===== 搜索 =====

    public void onSearchTextChanged(@Nullable CharSequence text) {
        keyword = text == null ? "" : text.toString().trim();
        if (keyword.isEmpty()) {
            tips.setValue(Collections.emptyList());
            tipsVisible.setValue(false);
        }
        searchText.onNext(keyword);
    }

    public void onTipSelected(@NonNull AddressTipVO tip) {
        tipsVisible.setValue(false);
        hideKeyboard.call();
        fromSearch = true;
        moveCamera.setValue(new MapPoint(tip.getLatitude(), tip.getLongitude(), null));
    }

    /** 搜索框收起结果卡片（返回键） */
    public void hideTips() {
        tipsVisible.setValue(false);
    }

    // ===== 定位 =====

    public void onLocateClick(boolean hasPermission) {
        if (!hasPermission) {
            requestPermission.call();
            return;
        }
        locate();
    }

    /** Notice 中的“允许定位” */
    public void onAllowLocationClick() {
        requestPermission.call();
    }

    public void onPermissionResult(boolean granted) {
        if (granted) {
            showPermissionNotice.setValue(false);
            locate();
        } else {
            showMessage(MSG_NO_PERMISSION);
        }
    }

    private void locate() {
        if (Boolean.TRUE.equals(locating.getValue()) || !mapAvailable) {
            return;
        }
        locating.setValue(true);
        launch(repository.locateOnce(), point -> {
            locating.setValue(false);
            myLocation.setValue(point);
            updateHint();
            moveCamera.setValue(point);
        }, e -> {
            locating.setValue(false);
            showMessage(MSG_LOCATE_FAILED);
        });
    }

    // ===== 手动补充、地图加载 =====

    public void onAddressLineClick() {
        LocationResult r = result.getValue();
        if (r == null || STATE_LOADING.equals(addressState.getValue())) {
            return;
        }
        manualAddress.setValue(nonNull(r.getAddress()));
        manualEditing.setValue(true);
    }

    public void onMapLoadFailed() {
        mapLoadFailed.setValue(true);
    }

    public void onReloadMap() {
        mapLoadFailed.setValue(false);
    }

    // ===== 确认 =====

    public void confirm() {
        if (!Boolean.TRUE.equals(canConfirm.getValue())) {
            return;
        }
        if (!mapAvailable) {
            String province = cut(manualProvince.getValue(), REGION_MAX);
            String city = cut(manualCity.getValue(), REGION_MAX);
            finishWithResult.setValue(new LocationResult(null, null, province, city,
                    cut(manualDistrict.getValue(), REGION_MAX), null, null, cut(manualAddress.getValue(), ADDRESS_MAX),
                    LocationResult.SOURCE_MANUAL, null));
            return;
        }
        LocationResult r = result.getValue();
        if (r == null) {
            return;
        }
        if (Boolean.TRUE.equals(manualEditing.getValue())) {
            String text = cut(manualAddress.getValue(), ADDRESS_MAX);
            if (!text.isEmpty()) {
                r = r.withAddress(text);
            }
        }
        finishWithResult.setValue(r);
    }

    private void updateCanConfirm() {
        boolean ok;
        if (Boolean.TRUE.equals(manualMode.getValue())) {
            ok = !isBlank(manualProvince.getValue()) && !isBlank(manualDistrict.getValue())
                    && !isBlank(manualAddress.getValue());
        } else {
            LocationResult r = result.getValue();
            ok = r != null && r.hasAdcode() && !STATE_LOADING.equals(addressState.getValue());
        }
        if (!Boolean.valueOf(ok).equals(canConfirm.getValue())) {
            canConfirm.setValue(ok);
        }
    }

    private void updateHint() {
        if (input.isChange()) {
            hintLine.setValue(HINT_CHANGE);
            return;
        }
        MapPoint loc = myLocation.getValue();
        if (loc != null && loc.getAccuracy() != null) {
            hintLine.setValue(HINT_DRAG + " · 定位精度约 " + loc.getAccuracy() + " 米");
        } else {
            hintLine.setValue(HINT_DRAG);
        }
    }

    @Nullable
    private static String cityOf(@Nullable LocationResult r) {
        return r == null ? null : r.getCity();
    }

    @NonNull
    private static String nonNull(@Nullable String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @NonNull
    private static String cut(@Nullable String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.codePointCount(0, t.length()) > max ? t.substring(0, t.offsetByCodePoints(0, max)) : t;
    }

    @VisibleForTesting
    @NonNull
    PickLocationInput getInput() {
        return input;
    }

    // ===== 对外 LiveData =====

    public LiveData<String> getTitleName() {
        return titleName;
    }

    public LiveData<LocationResult> getResult() {
        return result;
    }

    public LiveData<String> getAddressState() {
        return addressState;
    }

    public LiveData<MapPoint> getMyLocation() {
        return myLocation;
    }

    public LiveData<String> getHintLine() {
        return hintLine;
    }

    public LiveData<Boolean> getLocating() {
        return locating;
    }

    public LiveData<List<AddressTipVO>> getTips() {
        return tips;
    }

    public LiveData<Boolean> getTipsVisible() {
        return tipsVisible;
    }

    public LiveData<Boolean> getShowPermissionNotice() {
        return showPermissionNotice;
    }

    public LiveData<Boolean> getManualEditing() {
        return manualEditing;
    }

    public LiveData<Boolean> getMapLoadFailed() {
        return mapLoadFailed;
    }

    public MutableLiveData<String> getManualAddress() {
        return manualAddress;
    }

    public LiveData<Boolean> getManualMode() {
        return manualMode;
    }

    public MutableLiveData<String> getManualProvince() {
        return manualProvince;
    }

    public MutableLiveData<String> getManualCity() {
        return manualCity;
    }

    public MutableLiveData<String> getManualDistrict() {
        return manualDistrict;
    }

    public LiveData<Boolean> getCanConfirm() {
        return canConfirm;
    }

    public SingleLiveEvent<Void> getRequestPermission() {
        return requestPermission;
    }

    public SingleLiveEvent<MapPoint> getMoveCamera() {
        return moveCamera;
    }

    public SingleLiveEvent<Void> getHideKeyboard() {
        return hideKeyboard;
    }

    public SingleLiveEvent<LocationResult> getFinishWithResult() {
        return finishWithResult;
    }

    /** 逆地理编码结果（result 为 null 表示失败） */
    private static final class Geo {
        final MapPoint point;
        @Nullable
        final LocationResult result;

        Geo(MapPoint point, @Nullable LocationResult result) {
            this.point = point;
            this.result = result;
        }
    }
}
