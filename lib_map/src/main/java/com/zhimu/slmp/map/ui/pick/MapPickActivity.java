package com.zhimu.slmp.map.ui.pick;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.google.android.material.color.MaterialColors;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.map.AmapInitializer;
import com.zhimu.slmp.map.R;
import com.zhimu.slmp.map.contract.PickLocationContract;
import com.zhimu.slmp.map.databinding.MapActivityPickBinding;
import com.zhimu.slmp.map.di.MapInjector;
import com.zhimu.slmp.map.model.vo.AddressTipVO;
import com.zhimu.slmp.map.model.vo.LocationResult;
import com.zhimu.slmp.map.model.vo.MapPoint;
import com.zhimu.slmp.map.ui.MapLifecycleObserver;

import java.util.List;
import java.util.Map;

/**
 * F04 养殖场位置选点（MapPicker）：高德 TextureMapView + 中心固定的大头针（ImageView，不是 Marker）、浮动搜索框与结果卡片、
 * 定位按钮、底部地址卡。关闭旋转与倾斜；地图加载回调 10 秒未到时显示“地图没有加载出来”，搜索仍可用。
 * 未配置高德 Key（本期默认）时不创建 MapView，地图区显示占位说明，地址卡改为手动填写，保证 F02 / F06 流程能走通。
 */
public class MapPickActivity extends BaseActivity {

    private static final String TAG = "ZmtMap";
    private static final long MAP_LOAD_TIMEOUT_MS = 10_000;
    private static final int CAMERA_ANIM_MS = 300;
    private static final float ZOOM_DETAIL = 16f;
    private static final float ZOOM_PROVINCE = 6f;

    private MapActivityPickBinding binding;
    private MapPickViewModel vm;
    private TipAdapter tipAdapter;

    @Nullable
    private TextureMapView mapView;
    @Nullable
    private AMap aMap;
    @Nullable
    private MapLifecycleObserver mapObserver;
    @Nullable
    private Marker myMarker;
    @Nullable
    private Circle myCircle;
    private boolean mapLoaded;
    private boolean cameraMoving;
    private boolean ignoreSearchText;
    @Nullable
    private OnBackPressedCallback backCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.map_activity_pick);
        binding.setLifecycleOwner(this);
        vm = obtainViewModel(MapInjector.factory(), MapPickViewModel.class);
        observeBase(vm);
        binding.setVm(vm);
        TopAppBarHelper.setupBack(binding.topBar.baseToolbar, getString(R.string.map_pick_title));

        boolean available = AmapInitializer.isMapAvailable() && createMap(savedInstanceState);
        if (vm.isInitialized()) {
            vm.restoreCamera();
        } else {
            vm.init(hasLocationPermission(), available);
            if (available && vm.getMoveCamera().getValue() == null && aMap != null) {
                aMap.moveCamera(CameraUpdateFactory.zoomTo(ZOOM_PROVINCE));
            }
        }
        setupViews();
        observeViewModel();
    }

    /** 创建地图；失败（例如设备缺少地图 so）时返回 false，改为手动填写 */
    private boolean createMap(@Nullable Bundle savedInstanceState) {
        try {
            TextureMapView view = new TextureMapView(this);
            binding.mapContainer.addView(view);
            MapLifecycleObserver observer = new MapLifecycleObserver(view, savedInstanceState);
            getLifecycle().addObserver(observer);
            AMap map = view.getMap();
            UiSettings ui = map.getUiSettings();
            ui.setRotateGesturesEnabled(false);
            ui.setTiltGesturesEnabled(false);
            ui.setZoomControlsEnabled(false);
            ui.setMyLocationButtonEnabled(false);
            ui.setScaleControlsEnabled(false);
            map.setOnMapLoadedListener(() -> mapLoaded = true);
            map.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition position) {
                    if (!cameraMoving) {
                        cameraMoving = true;
                        vm.onCameraMoving();
                    }
                }

                @Override
                public void onCameraChangeFinish(CameraPosition position) {
                    cameraMoving = false;
                    if (position != null && position.target != null) {
                        vm.onCameraIdle(position.target.latitude, position.target.longitude);
                    }
                }
            });
            handler.postDelayed(() -> {
                if (!mapLoaded && !isFinishing()) {
                    vm.onMapLoadFailed();
                }
            }, MAP_LOAD_TIMEOUT_MS);
            mapView = view;
            aMap = map;
            mapObserver = observer;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "地图创建失败，改为手动填写地址", t);
            binding.mapContainer.removeAllViews();
            return false;
        }
    }

    private void setupViews() {
        tipAdapter = new TipAdapter(tip -> {
            ignoreSearchText = true;
            binding.mapSearchInput.setText(tip.getName());
            binding.mapSearchInput.setSelection(binding.mapSearchInput.length());
            ignoreSearchText = false;
            vm.onTipSelected(tip);
        });
        binding.mapTipsList.setLayoutManager(new LinearLayoutManager(this));
        binding.mapTipsList.setAdapter(tipAdapter);

        binding.mapSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!ignoreSearchText) {
                    vm.onSearchTextChanged(s);
                }
            }
        });
        binding.mapSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        ClickThrottle.setOnClickListener(binding.mapLocate, v -> vm.onLocateClick(hasLocationPermission()));
        binding.mapPermissionNotice.setAction(getString(R.string.map_permission_action),
                v -> vm.onAllowLocationClick());
        binding.mapAddressText.setOnClickListener(v -> vm.onAddressLineClick());
        ClickThrottle.setOnClickListener(binding.mapConfirm, v -> vm.confirm());
        binding.mapReload.setOnClickListener(v -> {
            vm.onReloadMap();
            recreate();
        });

        // 结果卡片展开时，返回键先收起卡片
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                vm.hideTips();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
    }

    private void observeViewModel() {
        vm.getAddressState().observe(this, s -> renderAddress());
        vm.getResult().observe(this, r -> renderAddress());
        vm.getManualMode().observe(this, m -> renderPlaceholder());
        vm.getMapLoadFailed().observe(this, f -> renderPlaceholder());
        vm.getTips().observe(this, this::renderTips);
        vm.getTipsVisible().observe(this, visible -> {
            OnBackPressedCallback cb = backCallback;
            if (cb != null) {
                cb.setEnabled(Boolean.TRUE.equals(visible));
            }
        });
        vm.getMoveCamera().observe(this, this::moveCamera);
        vm.getMyLocation().observe(this, this::renderMyLocation);
        vm.getHideKeyboard().observe(this, v -> {
            hideKeyboard(binding.mapSearchInput);
            binding.mapSearchInput.clearFocus();
        });
        vm.getRequestPermission().observe(this, v -> permissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}));
        vm.getFinishWithResult().observe(this, this::finishWithResult);
    }

    private void renderAddress() {
        String state = vm.getAddressState().getValue();
        LocationResult r = vm.getResult().getValue();
        CharSequence text;
        if (MapPickViewModel.STATE_LOADING.equals(state)) {
            text = getString(R.string.map_address_loading);
        } else if (MapPickViewModel.STATE_FAILED.equals(state)) {
            text = getString(R.string.map_address_failed);
        } else if (r != null && r.getAddress() != null) {
            text = r.getAddress();
        } else {
            text = getString(R.string.map_address_none);
        }
        binding.mapAddressText.setText(text);
        float lift = MapPickViewModel.STATE_LOADING.equals(state) ? -8 * getResources().getDisplayMetrics().density : 0;
        binding.mapPin.animate().translationY(lift).setDuration(150).start();
    }

    private void renderPlaceholder() {
        boolean manual = Boolean.TRUE.equals(vm.getManualMode().getValue());
        boolean failed = Boolean.TRUE.equals(vm.getMapLoadFailed().getValue());
        if (manual) {
            binding.mapPlaceholder.setVisibility(View.VISIBLE);
            binding.mapPlaceholderText.setText(R.string.map_no_key);
            binding.mapReload.setVisibility(View.GONE);
        } else if (failed) {
            binding.mapPlaceholder.setVisibility(View.VISIBLE);
            binding.mapPlaceholderText.setText(R.string.map_load_failed);
            binding.mapReload.setVisibility(View.VISIBLE);
        } else {
            binding.mapPlaceholder.setVisibility(View.GONE);
        }
    }

    private void renderTips(@Nullable List<AddressTipVO> tips) {
        tipAdapter.submitList(tips);
        binding.mapTipsEmpty.setVisibility(tips == null || tips.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void moveCamera(@Nullable MapPoint point) {
        if (point == null || aMap == null) {
            return;
        }
        LatLng target = new LatLng(point.getLatitude().doubleValue(), point.getLongitude().doubleValue());
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, ZOOM_DETAIL), CAMERA_ANIM_MS, null);
    }

    /** 当前位置：16 dp primary 圆点 + 3 dp 白描边；精度圈 primary 约 15% 透明度 */
    private void renderMyLocation(@Nullable MapPoint point) {
        if (point == null || aMap == null) {
            return;
        }
        LatLng ll = new LatLng(point.getLatitude().doubleValue(), point.getLongitude().doubleValue());
        int primary = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary);
        double radius = point.getAccuracy() == null ? 0 : point.getAccuracy();
        if (myCircle == null) {
            myCircle = aMap.addCircle(new CircleOptions().center(ll).radius(radius)
                    .fillColor(ColorUtils.setAlphaComponent(primary, 38)).strokeWidth(0f));
        } else {
            myCircle.setCenter(ll);
            myCircle.setRadius(radius);
        }
        if (myMarker == null) {
            myMarker = aMap.addMarker(new MarkerOptions().position(ll).anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromBitmap(locationDot(primary))));
        } else {
            myMarker.setPosition(ll);
        }
    }

    @NonNull
    private Bitmap locationDot(int primary) {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(size / 2f, size / 2f, 11 * density, paint);
        paint.setColor(primary);
        canvas.drawCircle(size / 2f, size / 2f, 8 * density, paint);
        return bmp;
    }

    private void onPermissionResult(@NonNull Map<String, Boolean> result) {
        boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        vm.onPermissionResult(granted);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void finishWithResult(@Nullable LocationResult result) {
        if (result == null) {
            return;
        }
        setResult(RESULT_OK, new Intent().putExtra(PickLocationContract.EXTRA_RESULT, result));
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapObserver != null) {
            mapObserver.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapObserver != null) {
            mapObserver.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        mapView = null;
        aMap = null;
    }
}
