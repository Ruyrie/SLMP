package com.zhimu.slmp.inspect.ui.form;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.ButtonLoading;
import com.zhimu.slmp.base.widget.GroupedListLayout;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.OptionVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.inspect.R;
import com.zhimu.slmp.inspect.databinding.InspectActivityFormBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.InspectionFormVO;
import com.zhimu.slmp.inspect.ui.common.InspectNav;
import com.zhimu.slmp.inspect.ui.common.OptionAdapter;
import com.zhimu.slmp.inspect.ui.common.SiteJudge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * I02 / I03 页面基类：绑定共用布局 inspect_activity_form（信息卡、巡检项目、标记为异常、说明、照片和视频、底部按钮），
 * 处理定位权限（首次先显示用途 Notice）、滚动到出错字段、放弃确认与跳转。子类给出 ViewModel 类型与补录特有的日期选择。
 */
public abstract class InspectFormActivity<VM extends InspectFormViewModel> extends BaseActivity {

    private static final String REQ_DISCARD = "inspect_form_discard";
    private static final String PREFS = "inspect_prefs";
    private static final String KEY_LOCATION_EXPLAINED = "location_explained";

    protected InspectActivityFormBinding binding;
    protected VM vm;
    private OptionAdapter executorAdapter;
    private final Map<Long, InspectItemGroupView> itemViews = new HashMap<>();
    private List<InspectionFormVO.TemplateItem> renderedItems = Collections.emptyList();
    private boolean lastThenReport;

    private final ActivityResultLauncher<String[]> locationLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                vm.onLocationPermission(granted);
            });
    private final ActivityResultLauncher<Intent> pageLauncher = Router.registerForResult(this, r -> {
    });

    @NonNull
    protected abstract Class<VM> viewModelClass();

    /** 子类绑定补录变量、日期框等 */
    protected void onBindExtra(@NonNull InspectActivityFormBinding b) {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.inspect_activity_form);
        vm = obtainViewModel(InspectInjector.factory(), viewModelClass());
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        onBindExtra(binding);
        if (vm.getMedia() != null) {
            // 照片和视频网格：必须在页面 STARTED 之前 bind（内部注册 Activity Result）
            binding.inspectMedia.bind(this, vm.getMedia());
        }
        binding.inspectMedia.setCameraTitle(vm.getTitleText().getValue());

        TopAppBarHelper.inflateMenu(binding.inspectTopBar.baseToolbar, R.menu.inspect_menu_form);
        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.inspectTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.inspect_menu_history) {
                Bundle extras = new Bundle();
                extras.putLong(RouterPath.EXTRA_PEN_ID, vm.getPenId());
                Router.open(this, RouterPath.I04, extras);
                return true;
            }
            return false;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBackPressed()) {
                    finish();
                }
            }
        });
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());
        vm.getTitleText().observe(this, t -> binding.inspectMedia.setCameraTitle(t + " · " + vm.getInfoTitle().getValue()));

        // 巡检项目
        vm.getTemplateItems().observe(this, items -> renderItems());
        vm.getSelections().observe(this, s -> renderItems());
        vm.getFieldErrors().observe(this, e -> renderItems());
        vm.getReadOnly().observe(this, r -> renderItems());

        // 位置
        vm.getLocation().observe(this, this::renderLocation);
        vm.getRequestLocation().observe(this, v -> requestLocation());
        binding.inspectLocationNotice.setAction(getString(R.string.inspect_form_location_allow), v -> {
            prefs().edit().putBoolean(KEY_LOCATION_EXPLAINED, true).apply();
            vm.setLocationNoticeVisible(false);
            launchLocationPermission();
        });

        // 标记为异常
        ClickThrottle.setOnClickListener(binding.inspectManualHead, v -> vm.clickManualSwitch());
        vm.getManualMarked().observe(this, on -> {
            boolean marked = Boolean.TRUE.equals(on);
            binding.inspectManualIcon.setBackgroundResource(marked ? R.drawable.inspect_bg_leading_error
                    : R.drawable.inspect_bg_leading_neutral);
            binding.inspectManualIcon.setImageResource(marked ? R.drawable.inspect_ic_flag_filled
                    : R.drawable.inspect_ic_flag);
            binding.inspectManualIcon.setImageTintList(ContextCompat.getColorStateList(this, marked
                    ? com.zhimu.slmp.base.R.color.zm_on_error_container : com.zhimu.slmp.base.R.color.zm_on_surface_variant));
        });
        vm.getFocusReason().observe(this, v -> binding.inspectReasonInput.post(() -> {
            binding.inspectReasonInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.inspectReasonInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }));
        binding.inspectReasonInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                vm.onReasonChanged();
            }
        });

        // 实际执行人
        executorAdapter = OptionAdapter.bind(binding.inspectExecutorInput, vm::onExecutorSelected);
        vm.getMembers().observe(this, this::renderMembers);
        vm.getExecutorId().observe(this, id -> executorAdapter.showSelected(binding.inspectExecutorInput, id));

        // 发现死淘
        ClickThrottle.setOnClickListener(binding.inspectDeathButton, v -> {
            Bundle extras = new Bundle();
            InspectionFormVO ctx = vm.getFormCtx().getValue();
            if (ctx != null && ctx.getBatches().size() == 1) {
                extras.putLong(RouterPath.EXTRA_BATCH_ID, ctx.getBatches().get(0).getBatchId());
            }
            extras.putLong(RouterPath.EXTRA_PEN_ID, vm.getPenId());
            Router.open(this, RouterPath.L11, extras);
        });

        // 底部按钮
        ClickThrottle.setOnClickListener(binding.inspectReportButton, v -> {
            lastThenReport = true;
            vm.submit(true);
        });
        ClickThrottle.setOnClickListener(binding.inspectSubmitButton, v -> {
            lastThenReport = false;
            vm.submit(false);
        });
        vm.getSubmitting().observe(this, s -> {
            boolean on = Boolean.TRUE.equals(s);
            ButtonLoading.setLoading(lastThenReport ? binding.inspectReportButton : binding.inspectSubmitButton, on);
            binding.inspectReportButton.setEnabled(!on);
            binding.inspectSubmitButton.setEnabled(!on);
            if (!on) {
                ButtonLoading.setLoading(binding.inspectReportButton, false);
                ButtonLoading.setLoading(binding.inspectSubmitButton, false);
            }
        });
        vm.getNavEvent().observe(this, t -> InspectNav.open(this, pageLauncher, t));
        vm.getFinishEvent().observe(this, m -> InspectNav.finishWithMessage(this, m));
        vm.getScrollTo().observe(this, this::scrollToField);
        vm.getShowDiscard().observe(this, v -> ConfirmDialogFragment.builder(REQ_DISCARD)
                .title(getString(R.string.inspect_form_discard_title))
                .message(getString(R.string.inspect_form_discard_message))
                .negative(getString(R.string.inspect_action_keep_editing))
                .positive(getString(R.string.inspect_action_discard))
                .build()
                .showSafely(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (InspectFormViewModel.ACTION_RETRY.equals(actionId)) {
            vm.submit(lastThenReport);
        } else if (InspectFormViewModel.ACTION_REFRESH.equals(actionId)) {
            vm.retry();
        }
    }

    // ===== 巡检项目 =====

    private void renderItems() {
        List<InspectionFormVO.TemplateItem> items = vm.getTemplateItems().getValue();
        if (items == null) {
            return;
        }
        GroupedListLayout container = binding.inspectItems;
        if (items != renderedItems) {
            container.removeAllViews();
            itemViews.clear();
            for (InspectionFormVO.TemplateItem item : items) {
                InspectItemGroupView v = new InspectItemGroupView(this);
                container.addView(v);
                itemViews.put(item.getItemId(), v);
            }
            renderedItems = items;
            container.updatePositions();
        }
        Map<Long, Long> sel = vm.getSelections().getValue();
        Map<String, String> errors = vm.getFieldErrors().getValue();
        boolean readOnly = Boolean.TRUE.equals(vm.getReadOnly().getValue());
        for (InspectionFormVO.TemplateItem item : items) {
            InspectItemGroupView v = itemViews.get(item.getItemId());
            if (v != null) {
                v.bind(item, sel == null ? null : sel.get(item.getItemId()),
                        errors == null ? null : errors.get(InspectFormRules.itemKey(item.getItemId())), readOnly,
                        vm::selectOption);
            }
        }
    }

    private void renderMembers(@Nullable List<OptionVO> members) {
        List<OptionAdapter.Item> items = new ArrayList<>();
        if (members != null) {
            for (OptionVO o : members) {
                Long id = com.zhimu.slmp.inspect.ui.common.Args.toLong(o.getValue());
                if (id == null) {
                    continue;
                }
                String label = o.getLabel() == null ? "" : o.getLabel();
                items.add(new OptionAdapter.Item(id, o.getHint() == null ? label : label + " · " + o.getHint(), null));
            }
        }
        executorAdapter.setItems(items);
        executorAdapter.showSelected(binding.inspectExecutorInput, vm.getExecutorId().getValue());
    }

    // ===== 位置 =====

    private void renderLocation(@Nullable LocationUi ui) {
        if (ui == null) {
            return;
        }
        binding.inspectLocationProgress.setVisibility(ui.isLocating() ? View.VISIBLE : View.GONE);
        binding.inspectLocationText.setText(ui.text);
        StatusTagView tag = binding.inspectLocationTag;
        if (ui.state == SiteJudge.State.OFF_SITE) {
            tag.setStatus(getString(R.string.inspect_form_tag_off_site), StatusTagView.WARNING);
            tag.setTagIcon(R.drawable.inspect_ic_wrong_location);
            tag.setVisibility(View.VISIBLE);
        } else if (ui.state == SiteJudge.State.NO_LOCATION) {
            tag.setStatus(getString(R.string.inspect_form_tag_no_location), StatusTagView.NEUTRAL);
            tag.setTagIcon(R.drawable.inspect_ic_location_off);
            tag.setVisibility(View.VISIBLE);
        } else {
            tag.setVisibility(View.GONE);
        }
        binding.inspectLocationText.setTextAppearance(ui.state == SiteJudge.State.ON_SITE
                ? com.zhimu.slmp.base.R.style.TextAppearance_Zmt_LabelLarge
                : com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
        binding.inspectLocationText.setTextColor(ContextCompat.getColor(this, ui.state == SiteJudge.State.NO_LOCATION
                || ui.isLocating() ? com.zhimu.slmp.base.R.color.zm_on_surface_variant
                : com.zhimu.slmp.base.R.color.zm_on_surface));
    }

    private void requestLocation() {
        if (hasLocationPermission()) {
            vm.onLocationPermission(true);
        } else if (!prefs().getBoolean(KEY_LOCATION_EXPLAINED, false)) {
            // 首次登记：先显示定位用途 Notice，点“允许定位”后才弹系统权限框
            vm.showLocationNotice();
        } else {
            launchLocationPermission();
        }
    }

    private void launchLocationPermission() {
        locationLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ===== 滚动到出错字段 =====

    private void scrollToField(@Nullable String key) {
        if (key == null) {
            return;
        }
        View target = null;
        if (key.startsWith("item_")) {
            Long id = com.zhimu.slmp.inspect.ui.common.Args.toLong(key.substring(5));
            target = id == null ? null : itemViews.get(id);
        } else if (InspectFormRules.F_MANUAL_REASON.equals(key)) {
            target = binding.inspectManualCard;
        } else if (InspectFormRules.F_REMARK.equals(key)) {
            target = binding.inspectRemarkLayout;
        } else if (InspectFormRules.F_EXECUTOR.equals(key)) {
            target = binding.inspectExecutorLayout;
        } else if (InspectFormRules.F_DATE.equals(key)) {
            target = binding.inspectDateLayout;
        } else if (InspectFormRules.F_MEDIA.equals(key)) {
            target = binding.inspectMedia;
        }
        View t = target;
        if (t == null || t.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.inspectScroll.post(() -> {
            Rect r = new Rect();
            t.getDrawingRect(r);
            binding.inspectScroll.offsetDescendantRectToMyCoords(t, r);
            binding.inspectScroll.smoothScrollTo(0, Math.max(0, r.top - getResources()
                    .getDimensionPixelSize(com.zhimu.slmp.base.R.dimen.base_space_4)));
        });
    }
}
