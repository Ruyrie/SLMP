package com.zhimu.slmp.feed.ui.weigh;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.dialog.DateTimePicker;
import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.mvvm.UiState;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.util.DateFormatter;
import com.zhimu.slmp.base.util.NumberFormatter;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedActivityWeighBinding;
import com.zhimu.slmp.feed.di.FeedInjector;
import com.zhimu.slmp.feed.model.vo.WeighBatchOptionVO;
import com.zhimu.slmp.feed.ui.common.FeedErrors;
import com.zhimu.slmp.feed.ui.common.FeedNav;
import com.zhimu.slmp.feed.ui.common.FeedViews;
import com.zhimu.slmp.feed.util.FeedFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * G05 称重登记 / 修改称重。提交成功：来源为 G06 时 setResult 后返回；否则打开 G06（同一批次）并关闭本页，
 * 成功提示以全局提示显示在 G06。
 */
public class WeighRegisterActivity extends BaseActivity {

    private static final String REQ_DATE = "feed_weigh_date";
    private static final String REQ_DISCARD = "feed_weigh_discard";
    private static final String REQ_BATCH = "feed_weigh_batch";

    private FeedActivityWeighBinding binding;
    private WeighRegisterViewModel vm;
    private WeighItemRowAdapter rowAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.feed_activity_weigh);
        vm = obtainViewModel(FeedInjector.factory(), WeighRegisterViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        TopAppBarHelper.setupBack(binding.feedTopBar.baseToolbar, getString(R.string.feed_weigh_title));
        binding.feedTopBar.baseToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.feedTopBar.baseToolbar.inflateMenu(R.menu.feed_menu_weigh);
        TopAppBarHelper.tintMenu(binding.feedTopBar.baseToolbar);
        binding.feedTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.feed_menu_growth) {
                openGrowth();
                return true;
            }
            return false;
        });
        TopAppBarHelper.bindLiftOnScroll(binding.feedTopBar.baseAppBar, binding.feedScroll);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!vm.onBack()) {
                    finish();
                }
            }
        });

        binding.feedState.setOnRetryListener(v -> vm.retry());
        vm.getUiState().observe(this, s -> render());
        vm.getMode().observe(this, m -> render());
        vm.getBlockNotice().observe(this, b -> render());
        vm.getNeedBatchPick().observe(this, n -> render());
        vm.getInputMode().observe(this, m -> renderInputMode());
        vm.getBatchCardTitle().observe(this, t -> renderBatchCard());
        vm.getBatchCardText().observe(this, t -> renderBatchCard());
        vm.getLastWeighText().observe(this, t -> renderBatchCard());
        vm.getGrowthPreview().observe(this, this::renderPreview);
        vm.getDeviationError().observe(this, e -> binding.feedWeighDevValue.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(binding.feedWeighDevValue,
                        Boolean.TRUE.equals(e) ? androidx.appcompat.R.attr.colorError
                                : com.google.android.material.R.attr.colorOnSurface)));

        binding.feedWeighModeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                vm.selectInputMode(checkedId == R.id.feed_weigh_mode_individual
                        ? WeighRegisterViewModel.MODE_INDIVIDUAL : WeighRegisterViewModel.MODE_SAMPLE);
            }
        });
        ClickThrottle.setOnClickListener(binding.feedWeighDateInput, v -> vm.onDateClick());
        binding.feedWeighDateLayout.setEndIconOnClickListener(v -> vm.onDateClick());
        binding.feedWeighBatchCard.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(vm.getNeedBatchPick().getValue())) {
                showBatchSheet();
            }
        });
        bindThousands(binding.feedWeighTotalInput);

        rowAdapter = new WeighItemRowAdapter(binding.feedWeighItems, new WeighItemRowAdapter.Listener() {
            @Override
            public void onWeightChanged(int index, @NonNull String text) {
                vm.setItemWeight(index, text);
            }

            @Override
            public void onRemove(int index) {
                vm.removeItemRow(index);
            }

            @Override
            public void onAddFromKeyboard() {
                vm.addItemRow();
            }
        });
        vm.getItemRows().observe(this, rows -> renderRows());
        vm.getFieldErrors().observe(this, e -> renderRows());
        binding.feedWeighAddItem.setOnClickListener(v -> {
            rowAdapter.focusLastOnNextSubmit();
            vm.addItemRow();
        });

        ClickThrottle.setOnClickListener(binding.feedWeighCycleRow, v -> vm.onCycleClick());
        vm.getCycleEditable().observe(this, e -> binding.feedWeighCycleRow.setClickable(Boolean.TRUE.equals(e)));

        binding.feedActionBar.setOnPrimaryClickListener(v -> vm.submit());
        vm.getCanSubmit().observe(this, c -> binding.feedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(c)));
        vm.getSubmitting().observe(this, s -> binding.feedActionBar.setPrimaryLoading(Boolean.TRUE.equals(s)));

        vm.getShowBatchSheet().observe(this, v -> showBatchSheet());
        vm.getShowCycleSheet().observe(this, v -> WeighCycleSheet.newInstance(getString(R.string.feed_weigh_cycle))
                .showSafely(getSupportFragmentManager()));
        vm.getShowDatePicker().observe(this, v -> pickDate());
        vm.getShowDiscardDialog().observe(this, v -> FeedViews.showDiscard(this, REQ_DISCARD));
        vm.getNavigateToGrowth().observe(this, this::onSubmitted);

        getSupportFragmentManager().setFragmentResultListener(REQ_DATE, this,
                (key, result) -> vm.onDateSelected(DateTimePicker.getDate(result)));
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                vm.onDiscardConfirmed();
                finish();
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_BATCH, this, (key, result) -> {
            String value = OptionSheetDialogFragment.getValue(result);
            if (value != null) {
                vm.selectBatch(Long.parseLong(value));
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) {
            vm.saveDraft();
        }
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (FeedErrors.ACTION_RESTORE_DRAFT.equals(actionId)) {
            vm.restoreDraft();
        } else if (FeedErrors.ACTION_REFRESH.equals(actionId)) {
            vm.retry();
        }
    }

    private void render() {
        UiState state = vm.getUiState().getValue();
        binding.feedState.render(state);
        boolean content = state == null || state.is(UiState.Status.CONTENT);
        boolean edit = vm.getMode().getValue() == WeighRegisterViewModel.WeighRegisterMode.EDIT;
        boolean blocked = vm.getBlockNotice().getValue() != null;
        boolean pick = Boolean.TRUE.equals(vm.getNeedBatchPick().getValue());
        binding.feedTopBar.baseToolbar.setTitle(edit ? R.string.feed_weigh_title_edit : R.string.feed_weigh_title);
        binding.feedTopBar.baseToolbar.getMenu().findItem(R.id.feed_menu_growth)
                .setVisible(content && vm.currentBatchId() != null && !pick);
        binding.feedWeighForm.setVisibility(blocked || pick ? View.GONE : View.VISIBLE);
        binding.feedActionBar.setVisibility(content && !blocked && !pick ? View.VISIBLE : View.GONE);
        binding.feedActionBar.setPrimary(getString(edit ? R.string.feed_weigh_action_save
                : R.string.feed_weigh_action_submit), 0);
        binding.feedActionBar.setPrimaryEnabled(Boolean.TRUE.equals(vm.getCanSubmit().getValue()));
        binding.feedWeighDateLayout.setEnabled(!edit);
        renderBatchCard();
    }

    private void renderBatchCard() {
        boolean pick = Boolean.TRUE.equals(vm.getNeedBatchPick().getValue());
        binding.feedWeighBatchCard.setTitle(pick ? getString(R.string.feed_weigh_pick_batch)
                : vm.getBatchCardTitle().getValue());
        String line1 = vm.getBatchCardText().getValue();
        String line2 = vm.getLastWeighText().getValue();
        String sub = pick ? null : (line1 == null ? "" : line1) + (line2 == null || line2.isEmpty() ? "" : "\n" + line2);
        binding.feedWeighBatchCard.setSubtitle(sub);
        binding.feedWeighBatchCard.setTrailingIcon(pick ? com.zhimu.slmp.base.R.drawable.ic_arrow_drop_down : 0);
        binding.feedWeighBatchCard.setClickable(pick);
    }

    /** 两种录入方式淡入淡出切换，各自保留已填的数据 */
    private void renderInputMode() {
        boolean individual = WeighRegisterViewModel.MODE_INDIVIDUAL.equals(vm.getInputMode().getValue());
        int checked = individual ? R.id.feed_weigh_mode_individual : R.id.feed_weigh_mode_sample;
        if (binding.feedWeighModeGroup.getCheckedButtonId() != checked) {
            binding.feedWeighModeGroup.check(checked);
        }
        fade(binding.feedWeighSampleBox, !individual);
        fade(binding.feedWeighIndividualBox, individual);
    }

    private static void fade(@NonNull View v, boolean show) {
        if (show == (v.getVisibility() == View.VISIBLE)) {
            return;
        }
        if (show) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
            v.animate().alpha(1f).setDuration(200).start();
        } else {
            v.setVisibility(View.GONE);
        }
    }

    private void renderRows() {
        rowAdapter.submit(vm.getItemRows().getValue(), vm.getFieldErrors().getValue(), true);
    }

    /** 生长标签（偏慢 trending_down / 偏快 trending_up）与卡片底部说明 */
    private void renderPreview(@Nullable GrowthPreview p) {
        StatusTagView tag = binding.feedWeighGrowthTag;
        if (p == null || p.tagText == null) {
            tag.setVisibility(View.GONE);
        } else {
            tag.setStatus(p.tagText, StatusTagView.WARNING);
            tag.setTagIcon(p.isFast() ? com.zhimu.slmp.base.R.drawable.ic_trending_up
                    : com.zhimu.slmp.base.R.drawable.ic_trending_down);
            tag.setVisibility(View.VISIBLE);
        }
        binding.feedWeighNote.setText(p == null ? null : p.note);
        binding.feedWeighNote.setVisibility(p == null || p.note == null ? View.GONE : View.VISIBLE);
    }

    private void pickDate() {
        LocalDate selected = DateFormatter.parseDate(vm.getWeighDate().getValue());
        DateTimePicker.date(REQ_DATE)
                .selection(selected != null ? selected : DateFormatter.today())
                .bounds(vm.minDate(), DateFormatter.today())
                .show(getSupportFragmentManager());
    }

    private void showBatchSheet() {
        List<WeighBatchOptionVO> list = vm.getBatchOptions().getValue();
        if (list == null || list.isEmpty()) {
            return;
        }
        OptionSheetDialogFragment.Builder b = OptionSheetDialogFragment.builder(REQ_BATCH)
                .title(getString(R.string.feed_batch_sheet_title));
        for (WeighBatchOptionVO o : list) {
            OptionSheetDialogFragment.Option option = new OptionSheetDialogFragment.Option(
                    String.valueOf(o.getBatchId()), FeedFormat.orEmpty(o.getBatchNo()),
                    FeedFormat.join(o.getPenNames(), o.getBreedName(),
                            o.getDayAge() == null ? null : o.getDayAge() + " 日龄"));
            if (o.getGrowthStatusText() != null && ("SLOW".equals(o.getGrowthStatus())
                    || "FAST".equals(o.getGrowthStatus()))) {
                option.tag(o.getGrowthStatusText(), StatusTagView.WARNING);
            }
            b.option(option);
        }
        Long current = vm.currentBatchId();
        b.selected(current == null ? null : String.valueOf(current));
        b.build().showSafely(getSupportFragmentManager());
    }

    /** show_chart：进入本批次 G06 */
    private void openGrowth() {
        Long id = vm.currentBatchId();
        if (id == null) {
            return;
        }
        if (vm.isFromGrowth()) {
            finish();
            return;
        }
        Router.open(this, RouterPath.G06, FeedNav.batchArgs(id));
    }

    private void onSubmitted(@NonNull UiMessage message) {
        Long id = vm.currentBatchId();
        if (vm.isFromGrowth() || id == null) {
            FeedNav.finishWithMessage(this, message);
            return;
        }
        Router.open(this, RouterPath.G06, FeedNav.batchArgs(id));
        setResult(Activity.RESULT_OK);
        finish();
        GlobalEvents.postMessage(message);
    }

    /** 总重输入时自动加千分位（只处理整数部分） */
    private static void bindThousands(@NonNull android.widget.EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            private boolean self;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (self) {
                    return;
                }
                String raw = s.toString().replace(",", "");
                if (raw.isEmpty() || !raw.matches("^\\d+(\\.\\d*)?$")) {
                    return;
                }
                int dot = raw.indexOf('.');
                String intPart = dot >= 0 ? raw.substring(0, dot) : raw;
                String frac = dot >= 0 ? raw.substring(dot) : "";
                String formatted;
                try {
                    formatted = NumberFormatter.formatNumber(new java.math.BigDecimal(intPart), 0) + frac;
                } catch (NumberFormatException e) {
                    return;
                }
                if (!formatted.equals(s.toString())) {
                    self = true;
                    s.replace(0, s.length(), formatted);
                    self = false;
                }
            }
        });
    }
}
