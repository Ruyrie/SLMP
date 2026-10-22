package com.zhimu.slmp.common.ui.change;

import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.textfield.TextInputLayout;
import com.zhimu.slmp.base.dialog.ConfirmDialogFragment;
import com.zhimu.slmp.base.event.GlobalEvents;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.R;
import com.zhimu.slmp.common.databinding.CommonChangeActivityFormBinding;
import com.zhimu.slmp.common.model.vo.ChangeRequestFormVO;
import com.zhimu.slmp.common.ui.change.model.vo.ChangeFormVO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * U01 提交修改申请（RouterPath.U01）：参数 EXTRA_BIZ_TYPE、EXTRA_BIZ_ID，重新提交时 EXTRA_REQUEST_ID。
 * 成功后 RESULT_OK 返回来源页并提示“已提交修改申请，等待管理员审核”；已填写内容时返回先确认。
 */
public class ChangeRequestFormActivity extends BaseActivity {

    private static final String REQ_DISCARD = "common_change_discard";

    private ChangeRequestFormViewModel vm;
    private CommonChangeActivityFormBinding binding;
    private final List<Option> options = new ArrayList<>();

    /** 修改项菜单的一项 */
    private static final class Option {
        final String code;
        final String label;
        final boolean danger;

        Option(String code, String label, boolean danger) {
            this.code = code;
            this.label = label;
            this.danger = danger;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = obtainViewModel(ChangeComponent.Injector.factory(this), ChangeRequestFormViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.common_change_activity_form);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);
        observeBase(vm);
        TopAppBarHelper.setupBack(binding.commonChangeFormTopBar.baseToolbar, getString(R.string.common_change_form_title));
        TopAppBarHelper.bindLiftOnScroll(binding.commonChangeFormTopBar.baseAppBar, binding.commonChangeFormScroll);
        binding.commonChangeFormState.setOnRetryListener(v -> vm.retry());
        binding.commonChangeOldValueInput.setPaintFlags(
                binding.commonChangeOldValueInput.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        vm.getForm().observe(this, this::renderForm);
        vm.selectedField.observe(this, code -> renderField());
        vm.getRejectReason().observe(this, r -> {
            binding.commonChangeReject.setVisibility(r == null ? View.GONE : View.VISIBLE);
            binding.commonChangeReject.setText(r == null ? null : getString(R.string.common_change_reject_prefix, r));
        });
        vm.getFieldErrors().observe(this, this::renderErrors);
        vm.getFocusField().observe(this, this::focus);
        binding.commonChangeFieldInput.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < options.size()) {
                vm.selectField(options.get(position).code);
            }
        });

        binding.commonChangeFormBar.setOnPrimaryClickListener(v -> vm.submit());
        binding.commonChangeFormBar.setOnSecondaryClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (vm.isDirty()) {
                    ConfirmDialogFragment.builder(REQ_DISCARD)
                            .title(getString(R.string.common_change_discard_title))
                            .message(getString(R.string.common_change_discard_message))
                            .positive(getString(R.string.common_change_discard))
                            .negative(getString(R.string.common_change_keep))
                            .build()
                            .showSafely(getSupportFragmentManager());
                } else {
                    finish();
                }
            }
        });
        getSupportFragmentManager().setFragmentResultListener(REQ_DISCARD, this, (key, result) -> {
            if (ConfirmDialogFragment.isConfirmed(result)) {
                finish();
            }
        });
        vm.getSubmitted().observe(this, vo -> {
            setResult(Activity.RESULT_OK);
            finish();
            GlobalEvents.postMessage(ChangeRequestFormViewModel.MSG_SUBMITTED);
        });
        vm.getCloseForbidden().observe(this, c -> finish());
    }

    private void renderForm(@Nullable ChangeFormVO f) {
        if (f == null) {
            return;
        }
        binding.commonChangeReviewTip.setText(f.getReviewTip());
        binding.commonChangeReviewTip.setVisibility(f.getReviewTip() == null ? View.GONE : View.VISIBLE);
        if (vm.hasPending()) {
            String[] parts = splitConclusion(ChangeRequestFormViewModel.MSG_PENDING);
            binding.commonChangePending.setNotice(parts[0], parts[1]);
            binding.commonChangePending.setVisibility(View.VISIBLE);
        } else {
            binding.commonChangePending.setVisibility(View.GONE);
        }

        String typeText = ChangeBizTypes.textOf(f.getBizType(), f.getBizTypeText());
        binding.commonChangeRecordIcon.setImageResource(ChangeBizTypes.iconOf(f.getBizType()));
        String title = f.getRecordTitle();
        String subtitle = f.getRecordSubtitle();
        if (title == null) {
            String locator = f.getRecordLocator() == null ? "" : f.getRecordLocator();
            int idx = locator.indexOf(" · ");
            title = typeText + (idx > 0 ? " · " + locator.substring(0, idx) : locator.isEmpty() ? "" : " · " + locator);
            if (subtitle == null && idx > 0) {
                subtitle = locator.substring(idx + 3);
            }
        }
        binding.commonChangeRecordTitle.setText(title);
        binding.commonChangeRecordSubtitle.setText(subtitle);
        binding.commonChangeRecordSubtitle.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        renderRecordCard(f.getRecordCard());

        options.clear();
        for (ChangeRequestFormVO.Field field : f.getFields()) {
            options.add(new Option(field.getFieldCode(), field.getFieldName() == null ? "" : field.getFieldName(), false));
        }
        if (f.isVoidAllowed()) {
            options.add(new Option(ChangeRequestFormViewModel.VOID_CODE, getString(R.string.common_change_void_option), true));
        }
        binding.commonChangeFieldInput.setAdapter(new OptionAdapter(this, options));
        renderField();
    }

    private void renderRecordCard(@NonNull List<ChangeRequestFormVO.CardItem> items) {
        LinearLayout box = binding.commonChangeRecordCard;
        box.removeAllViews();
        for (ChangeRequestFormVO.CardItem item : items) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(this);
            label.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
            label.setTextColor(com.google.android.material.color.MaterialColors.getColor(box,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            label.setText(item.getLabel());
            TextView value = new TextView(this);
            value.setTextAppearance(com.zhimu.slmp.base.R.style.TextAppearance_Zmt_BodyMedium);
            value.setTextColor(com.google.android.material.color.MaterialColors.getColor(box,
                    com.google.android.material.R.attr.colorOnSurface));
            value.setText(item.getValue());
            col.addView(label);
            col.addView(value);
            box.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void renderField() {
        ChangeFormVO f = vm.getForm().getValue();
        if (f == null) {
            return;
        }
        boolean isVoid = vm.isVoidSelected();
        String code = vm.selectedField.getValue();
        String label = null;
        for (Option o : options) {
            if (o.code != null && o.code.equals(code)) {
                label = o.label;
            }
        }
        binding.commonChangeFieldInput.setText(label, false);

        ChangeRequestFormVO.Field field = vm.currentField();
        binding.commonChangeValueRow.setVisibility(isVoid ? View.GONE : View.VISIBLE);
        binding.commonChangeVoidNotice.setVisibility(isVoid ? View.VISIBLE : View.GONE);
        if (isVoid) {
            String[] parts = splitConclusion(f.getVoidNotice() == null ? "" : f.getVoidNotice());
            binding.commonChangeVoidNotice.setNotice(parts[0], parts[1]);
        }
        String unit = field == null ? null : field.getValueUnit();
        binding.commonChangeOldValue.setSuffixText(unit);
        binding.commonChangeNewValue.setSuffixText(unit);
        binding.commonChangeOldValueInput.setText(field == null ? null : field.getValue());
        binding.commonChangeNewValue.setHelperText(f.hintOf(code));
        boolean number = field == null || "NUMBER".equals(field.getInputType());
        binding.commonChangeNewValueInput.setInputType(number
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT);

        binding.commonChangeRecalcTags.removeAllViews();
        List<String> tags = vm.currentRecalcTags();
        for (String t : tags) {
            StatusTagView tag = new StatusTagView(this);
            tag.setStatus(t, StatusTagView.NEUTRAL);
            binding.commonChangeRecalcTags.addView(tag);
        }
        int vis = tags.isEmpty() ? View.GONE : View.VISIBLE;
        binding.commonChangeRecalcTitle.setVisibility(vis);
        binding.commonChangeRecalcTags.setVisibility(vis);
    }

    private void renderErrors(@Nullable Map<String, String> errors) {
        setError(binding.commonChangeField, errors, ChangeRequestFormViewModel.FIELD_FIELD_CODE);
        setError(binding.commonChangeNewValue, errors, ChangeRequestFormViewModel.FIELD_NEW_VALUE);
        setError(binding.commonChangeReason, errors, ChangeRequestFormViewModel.FIELD_REASON);
    }

    private static void setError(@NonNull TextInputLayout til, @Nullable Map<String, String> errors, @NonNull String key) {
        String msg = errors == null ? null : errors.get(key);
        til.setError(msg);
        if (msg == null) {
            til.setErrorEnabled(false);
        }
    }

    private void focus(@Nullable String key) {
        EditText target = null;
        if (ChangeRequestFormViewModel.FIELD_NEW_VALUE.equals(key)) {
            target = binding.commonChangeNewValueInput;
        } else if (ChangeRequestFormViewModel.FIELD_REASON.equals(key)) {
            target = binding.commonChangeReasonInput;
        } else if (ChangeRequestFormViewModel.FIELD_FIELD_CODE.equals(key)) {
            binding.commonChangeFieldInput.requestFocus();
            return;
        }
        if (target != null) {
            target.requestFocus();
            EditText t = target;
            t.post(() -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(t, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    @NonNull
    private static String[] splitConclusion(@NonNull String text) {
        int idx = text.indexOf('。');
        if (idx < 0 || idx == text.length() - 1) {
            return new String[]{null, text};
        }
        return new String[]{text.substring(0, idx + 1), text.substring(idx + 1)};
    }

    @Override
    protected void onMessageAction(@NonNull String actionId) {
        if (ChangeRequestFormViewModel.ACTION_RETRY.equals(actionId)) {
            vm.submit();
        } else if (ChangeRequestFormViewModel.ACTION_REFRESH.equals(actionId)) {
            vm.retry();
        }
    }

    /** 修改项菜单：“作废这条记录”放在最后，上方分隔线，图标和文字为 error 色 */
    private static final class OptionAdapter extends ArrayAdapter<Option> {

        OptionAdapter(@NonNull Context context, @NonNull List<Option> items) {
            super(context, R.layout.common_change_item_field_option, R.id.common_change_option_text, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(getContext()).inflate(R.layout.common_change_item_field_option, parent, false);
            Option o = getItem(position);
            boolean danger = o != null && o.danger;
            TextView text = v.findViewById(R.id.common_change_option_text);
            ImageView icon = v.findViewById(R.id.common_change_option_icon);
            text.setText(o == null ? "" : o.label);
            text.setTextColor(com.google.android.material.color.MaterialColors.getColor(v, danger
                    ? androidx.appcompat.R.attr.colorError : com.google.android.material.R.attr.colorOnSurface));
            icon.setVisibility(danger ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.common_change_option_divider).setVisibility(danger ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
