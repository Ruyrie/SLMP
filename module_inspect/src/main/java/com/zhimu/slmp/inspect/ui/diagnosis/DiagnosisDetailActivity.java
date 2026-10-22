package com.zhimu.slmp.inspect.ui.diagnosis;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhimu.slmp.base.R;
import com.zhimu.slmp.base.dialog.MoreMenuHelper;
import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.base.widget.TopAppBarHelper;
import com.zhimu.slmp.common.model.vo.MediaItemVO;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.inspect.databinding.InspectActivityDiagnosisBinding;
import com.zhimu.slmp.inspect.di.InspectInjector;
import com.zhimu.slmp.inspect.model.vo.DiagnosisDetailVO;
import com.zhimu.slmp.inspect.model.vo.DiagnosisResultVO;
import com.zhimu.slmp.inspect.model.vo.HandleVO;
import com.zhimu.slmp.inspect.ui.common.InspectFormat;
import com.zhimu.slmp.inspect.ui.common.InspectNav;
import com.zhimu.slmp.inspect.ui.history.RecordLogBottomSheet;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.util.MediaGlide;

import java.util.List;

/**
 * I06 诊断详情：入口为 I05 提交后（诊断中轮询）、T06 消息中心的诊断消息、I01 已上报的异常行；员工只能打开自己上报的诊断
 * （无权时整页 U03）。所有者、场长底部“登记处理结果 / 修改处理结果”；待诊断时上报人、所有者、场长可“重新诊断”；结案后无底栏。
 */
public class DiagnosisDetailActivity extends BaseActivity {

    private static final int MENU_INSPECTION = 1;
    private static final int MENU_LOGS = 2;

    private InspectActivityDiagnosisBinding binding;
    private DiagnosisDetailViewModel vm;
    private boolean expanded;
    @Nullable
    private ObjectAnimator skeleton;

    private final ActivityResultLauncher<Intent> handleLauncher = Router.registerForResult(this, r -> {
        if (r.getResultCode() == Activity.RESULT_OK) {
            vm.onHandleResult();
        }
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, com.zhimu.slmp.inspect.R.layout.inspect_activity_diagnosis);
        vm = obtainViewModel(InspectInjector.factory(), DiagnosisDetailViewModel.class);
        observeBase(vm);
        binding.setLifecycleOwner(this);
        binding.setVm(vm);

        binding.inspectTopBar.baseToolbar.setNavigationOnClickListener(v -> finish());
        binding.inspectTopBar.baseToolbar.setOnMenuItemClickListener(item -> {
            showMore(binding.inspectTopBar.baseToolbar.findViewById(com.zhimu.slmp.inspect.R.id.inspect_menu_more));
            return true;
        });
        vm.getUiState().observe(this, binding.inspectState::render);
        binding.inspectState.setOnRetryListener(v -> vm.retry());
        binding.inspectState.setOnNoPermissionActionListener(v -> finish());

        vm.getDetail().observe(this, this::render);
        vm.getAiCardState().observe(this, s -> renderAi());
        vm.getDiagnosis().observe(this, d -> renderAi());
        vm.getRediagnosing().observe(this, r -> renderActions());
        vm.getNavEvent().observe(this, t -> InspectNav.open(this, handleLauncher, t));
        vm.getShowRecordLogs().observe(this, v -> RecordLogBottomSheet.newInstance(DiagnosisDetailViewModel.class)
                .showSafely(getSupportFragmentManager()));
        ClickThrottle.setOnClickListener(binding.inspectReportFooter, v -> {
            expanded = !expanded;
            binding.inspectReportExpand.setVisibility(expanded ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onDestroy() {
        if (skeleton != null) {
            skeleton.cancel();
        }
        super.onDestroy();
    }

    // ===== 渲染 =====

    private void render(@Nullable DiagnosisDetailVO d) {
        if (d == null) {
            return;
        }
        renderSteps(d);
        DiagnosisDetailVO.DiagnosisReport r = d.getReport();
        if (r != null) {
            binding.inspectReportPen.setText(r.getPenName());
            binding.inspectReportBatch.setText(r.getBatchNo());
            binding.inspectReportStock.setText(withUnit(String.valueOf(r.getStockSnapshot()), "头"));
            binding.inspectReportSick.setText(withUnit(String.valueOf(r.getSickCount()), "头"));
            binding.inspectReportRate.setText(withUnit(InspectFormat.percent1(r.getSickRate()), "%"));
            String who = r.getReporter() == null || r.getReporter().getName() == null ? "" : r.getReporter().getName();
            String head = who + " " + InspectFormat.time(r.getReportedAt()) + " 上报";
            DiagnosisDetailVO.FailureInfo f = d.getLastFailure();
            String footer;
            if (DiagnosisDetailVO.AI_UNAVAILABLE.equals(d.getAiState()) && f != null) {
                footer = head + InspectFormat.DOT + InspectFormat.time(f.getFailedAt()) + " " + f.getReasonText();
                expanded = true;
            } else {
                footer = head + InspectFormat.DOT + InspectFormat.join(r.getSymptomNames(), "未选症状")
                        + InspectFormat.DOT + r.getMediaCount() + " 个照片和视频";
            }
            binding.inspectReportFooter.setText(footer);
            binding.inspectReportExpand.setVisibility(expanded ? View.VISIBLE : View.GONE);
            binding.inspectReportSymptoms.removeAllViews();
            for (String s : r.getSymptomNames()) {
                StatusTagView tag = new StatusTagView(this);
                tag.setStatus(s, StatusTagView.NEUTRAL);
                binding.inspectReportSymptoms.addView(tag);
            }
            binding.inspectReportDescription.setText(r.getDescription());
            binding.inspectReportDescription.setVisibility(TextUtils.isEmpty(r.getDescription()) ? View.GONE : View.VISIBLE);
            renderThumbs(r.getMedia(), r.getPenName());
        }
        renderHandle(d);
        renderActions();
        DiagnosisDetailVO.DiagnosisPermissions p = d.getPermissions();
        binding.inspectTopBar.baseToolbar.getMenu().clear();
        boolean menu = (p.isCanViewInspection() && r != null && r.getInspectionId() != null)
                || (p.isCanViewRevisions() && d.getHandle() != null && d.getHandle().isHasRevisions());
        if (menu) {
            TopAppBarHelper.inflateMenu(binding.inspectTopBar.baseToolbar, com.zhimu.slmp.inspect.R.menu.inspect_menu_more);
        }
    }

    @NonNull
    private CharSequence withUnit(@NonNull String value, @NonNull String unit) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(value);
        int s = sb.length();
        sb.append(" ").append(unit);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.64f), s, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.zm_on_surface_variant)), s, sb.length(), 0);
        return sb;
    }

    /** 缩略图一行三格，超过 3 个时第 3 格叠“+3” */
    private void renderThumbs(@NonNull List<MediaItemVO> media, @Nullable String pen) {
        LinearLayout row = binding.inspectReportThumbs;
        row.removeAllViews();
        row.setVisibility(media.isEmpty() ? View.GONE : View.VISIBLE);
        int gap = getResources().getDimensionPixelSize(R.dimen.base_space_2);
        int count = Math.min(3, media.size());
        for (int i = 0; i < count; i++) {
            MediaItemVO m = media.get(i);
            FrameLayout cell = new FrameLayout(this);
            ShapeableImageView img = new ShapeableImageView(this);
            img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            img.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                    .setAllCornerSizes(getResources().getDimension(R.dimen.base_radius_md)).build());
            img.setBackgroundResource(com.zhimu.slmp.inspect.R.drawable.inspect_bg_leading_neutral);
            Object model = m.getThumbUrl() != null ? MediaGlide.thumbModel(m.getThumbUrl(), m.getFileId())
                    : MediaGlide.model(m.getUrl(), m.getFileId());
            Glide.with(img).load(model).centerCrop().into(img);
            cell.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            if (i == 2 && media.size() > 3) {
                TextView more = new TextView(this);
                more.setText("+" + (media.size() - 3));
                more.setGravity(android.view.Gravity.CENTER);
                more.setTextAppearance(R.style.TextAppearance_Zmt_LabelMedium);
                more.setTextColor(getColor(R.color.zm_on_media));
                more.setBackgroundResource(com.zhimu.slmp.inspect.R.drawable.inspect_bg_thumb_mask);
                cell.addView(more, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
            final int index = i;
            ClickThrottle.setOnClickListener(cell, v -> MediaViewer.openFiles(this, media, index,
                    "异常上报" + (pen == null ? "" : InspectFormat.DOT + pen)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 0, 1f);
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            row.addView(cell, lp);
            cell.post(() -> {
                ViewGroup.LayoutParams p = cell.getLayoutParams();
                p.height = cell.getWidth();
                cell.setLayoutParams(p);
            });
        }
        for (int i = count; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMarginStart(gap);
            row.addView(new View(this), lp);
        }
    }

    /** 进度条：待诊断、已诊断、已处理、已结案；AI 占位时停在“待诊断”转圈 */
    private void renderSteps(@NonNull DiagnosisDetailVO d) {
        Integer ai = vm.getAiCardState().getValue();
        if (ai != null && ai == DiagnosisDetailViewModel.AI_PLACEHOLDER) {
            binding.inspectSteps.setStep(0, true);
            return;
        }
        int step;
        switch (d.getStatus() == null ? "" : d.getStatus()) {
            case DiagnosisDetailVO.STATUS_CLOSED:
                step = 4;
                break;
            case DiagnosisDetailVO.STATUS_HANDLED:
                step = 2;
                break;
            case DiagnosisDetailVO.STATUS_DIAGNOSED:
                step = 1;
                break;
            default:
                step = 0;
                break;
        }
        binding.inspectSteps.setStep(step, step == 0);
    }

    private void renderAi() {
        Integer state = vm.getAiCardState().getValue();
        DiagnosisResultVO dx = vm.getDiagnosis().getValue();
        boolean placeholder = state != null && state == DiagnosisDetailViewModel.AI_PLACEHOLDER;
        boolean result = state != null && state == DiagnosisDetailViewModel.AI_RESULT && dx != null;
        binding.inspectAiPlaceholder.setVisibility(placeholder ? View.VISIBLE : View.GONE);
        binding.inspectAiCard.setVisibility(result ? View.VISIBLE : View.GONE);
        animateSkeleton(placeholder);
        DiagnosisDetailVO detail = vm.getDetail().getValue();
        if (placeholder) {
            binding.inspectSteps.setStep(0, true);
        } else if (detail != null) {
            renderSteps(detail);
        }
        if (!result) {
            renderActions();
            return;
        }
        binding.inspectAiCard.setAlpha(0f);
        binding.inspectAiCard.animate().alpha(1f).setDuration(300).start();
        String urgency = dx.getUrgency() == null ? DiagnosisResultVO.URGENCY_NORMAL : dx.getUrgency();
        switch (urgency) {
            case DiagnosisResultVO.URGENCY_URGENT:
                binding.inspectAiUrgency.setStatus(dx.getUrgencyText() == null ? "紧急" : dx.getUrgencyText(),
                        StatusTagView.ERROR);
                binding.inspectAiUrgency.setTagIcon(R.drawable.ic_emergency);
                break;
            case DiagnosisResultVO.URGENCY_ATTENTION:
                binding.inspectAiUrgency.setStatus(dx.getUrgencyText() == null ? "关注" : dx.getUrgencyText(),
                        StatusTagView.WARNING);
                binding.inspectAiUrgency.setTagIcon(R.drawable.ic_priority_high);
                break;
            default:
                binding.inspectAiUrgency.setStatus(dx.getUrgencyText() == null ? "一般" : dx.getUrgencyText(),
                        StatusTagView.NEUTRAL);
                binding.inspectAiUrgency.setTagIcon(0);
                break;
        }
        binding.inspectAiConclusion.setText(dx.getConclusion());
        binding.inspectAiDisclaimer.setText(dx.getDisclaimer());
        LinearLayout causes = binding.inspectAiCauses;
        causes.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        List<DiagnosisResultVO.DiagnosisCause> list = dx.getCauses();
        for (int i = 0; i < list.size(); i++) {
            DiagnosisResultVO.DiagnosisCause c = list.get(i);
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(getColor(R.color.zm_outline_variant));
                causes.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            View row = inflater.inflate(com.zhimu.slmp.inspect.R.layout.inspect_item_cause, causes, false);
            ((TextView) row.findViewById(com.zhimu.slmp.inspect.R.id.inspect_cause_rank))
                    .setText(String.valueOf(c.getRankNo() > 0 ? c.getRankNo() : i + 1));
            ((TextView) row.findViewById(com.zhimu.slmp.inspect.R.id.inspect_cause_name)).setText(c.getDiseaseName());
            StatusTagView major = row.findViewById(com.zhimu.slmp.inspect.R.id.inspect_cause_major);
            if (c.isMajorEpidemic()) {
                major.setStatus(getString(com.zhimu.slmp.inspect.R.string.inspect_diag_major), StatusTagView.ERROR);
                major.setTagIcon(R.drawable.ic_emergency);
                major.setVisibility(View.VISIBLE);
            }
            int fill = DiagnosisResultVO.LIKELIHOOD_HIGH.equals(c.getLikelihood()) ? 75
                    : DiagnosisResultVO.LIKELIHOOD_LOW.equals(c.getLikelihood()) ? 20 : 45;
            ((LinearProgressIndicator) row.findViewById(com.zhimu.slmp.inspect.R.id.inspect_cause_bar)).setProgress(fill);
            ((TextView) row.findViewById(com.zhimu.slmp.inspect.R.id.inspect_cause_level)).setText(c.getLikelihoodText());
            causes.addView(row);
        }
        LinearLayout advices = binding.inspectAiAdvices;
        advices.removeAllViews();
        List<String> adv = dx.getAdvices();
        for (int i = 0; i < adv.size(); i++) {
            TextView t = new TextView(this);
            t.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
            t.setTextColor(getColor(R.color.zm_on_surface));
            String prefix = (i + 1) + ". ";
            android.text.SpannableString ss = new android.text.SpannableString(prefix + adv.get(i));
            // 换行时悬挂缩进与正文对齐
            ss.setSpan(new android.text.style.LeadingMarginSpan.Standard(0,
                    (int) t.getPaint().measureText(prefix)), 0, ss.length(), 0);
            t.setText(ss);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = getResources().getDimensionPixelSize(R.dimen.base_space_1);
            advices.addView(t, lp);
        }
        renderActions();
    }

    private void animateSkeleton(boolean on) {
        if (on) {
            if (skeleton == null) {
                skeleton = ObjectAnimator.ofFloat(binding.inspectAiPlaceholder, View.ALPHA, 1f, 0.6f);
                skeleton.setDuration(600);
                skeleton.setRepeatMode(ValueAnimator.REVERSE);
                skeleton.setRepeatCount(ValueAnimator.INFINITE);
            }
            if (!skeleton.isRunning()) {
                skeleton.start();
            }
        } else if (skeleton != null) {
            skeleton.cancel();
            binding.inspectAiPlaceholder.setAlpha(1f);
        }
    }

    /** 处理结果卡：诊断人、处理方式、用药（已转为用药记录 → D05）、转归（治愈 / 死淘标签 + 合计进度）、AI 诊断 */
    private void renderHandle(@NonNull DiagnosisDetailVO d) {
        HandleVO h = d.getHandle();
        boolean staff = !d.getPermissions().isCanHandle();
        binding.inspectStaffHint.setVisibility(h == null && staff
                && DiagnosisDetailVO.AI_DONE.equals(d.getAiState()) ? View.VISIBLE : View.GONE);
        binding.inspectHandleCard.setVisibility(h == null ? View.GONE : View.VISIBLE);
        if (h == null) {
            return;
        }
        LinearLayout body = binding.inspectHandleBody;
        body.removeAllViews();
        if (d.isClosed()) {
            TextView lock = new TextView(this);
            lock.setText(com.zhimu.slmp.inspect.R.string.inspect_diag_closed);
            lock.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall);
            lock.setTextColor(getColor(R.color.zm_on_surface_variant));
            lock.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_lock, 0, 0, 0);
            lock.setCompoundDrawableTintList(getColorStateList(R.color.zm_on_surface_variant));
            lock.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.base_space_1));
            body.addView(lock);
        }
        LinearLayout title = new LinearLayout(this);
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(com.zhimu.slmp.inspect.R.string.inspect_diag_handle_title);
        t.setTextAppearance(R.style.TextAppearance_Zmt_TitleMedium);
        t.setTextColor(getColor(R.color.zm_on_surface));
        title.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView who = new TextView(this);
        who.setText((h.getHandledByName() == null ? "" : h.getHandledByName()) + " "
                + InspectFormat.time(h.getHandledAt()) + " 登记");
        who.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall);
        who.setTextColor(getColor(R.color.zm_on_surface_variant));
        title.addView(who);
        body.addView(title);
        addKv(body, "诊断人", h.getDiagnostician(), null);
        addKv(body, "处理方式", h.getTreatment(), null);
        if (h.isUsedDrug() && h.isMedicationConverted()) {
            addKv(body, "用药", "使用了药品 · ", "已转为用药记录");
        } else {
            addKv(body, "用药", h.isUsedDrug() ? "使用了药品" : "未使用药品", null);
        }
        // 转归
        LinearLayout outcome = new LinearLayout(this);
        outcome.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tags = new LinearLayout(this);
        StatusTagView cured = new StatusTagView(this);
        cured.setStatus("治愈 " + h.getCuredCount() + " 头", StatusTagView.SUCCESS);
        tags.addView(cured);
        StatusTagView culled = new StatusTagView(this);
        culled.setStatus("死淘 " + h.getCulledCount() + " 头", StatusTagView.NEUTRAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.base_space_2));
        tags.addView(culled, clp);
        outcome.addView(tags);
        int sick = d.getReport() == null ? 0 : d.getReport().getSickCount();
        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(1000);
        bar.setTrackThickness(getResources().getDimensionPixelSize(R.dimen.base_space_1));
        bar.setProgress(sick <= 0 ? 0 : Math.min(1000, h.getTotalCount() * 1000 / sick));
        progressRow.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView total = new TextView(this);
        total.setText("合计 " + h.getTotalCount() + " / " + sick + " 头");
        total.setTextAppearance(R.style.TextAppearance_Zmt_BodySmall_Number);
        total.setTextColor(getColor(R.color.zm_on_surface_variant));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.base_space_2));
        progressRow.addView(total, tlp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = getResources().getDimensionPixelSize(R.dimen.base_space_1);
        outcome.addView(progressRow, plp);
        addKvView(body, "转归", outcome);
        addKv(body, "AI 诊断", h.getAiMatchText(), null);
    }

    private void addKv(@NonNull LinearLayout body, @NonNull String key, @Nullable String value, @Nullable String link) {
        TextView v = new TextView(this);
        v.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
        v.setTextColor(getColor(R.color.zm_on_surface));
        if (link == null) {
            v.setText(value);
        } else {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(value == null ? "" : value);
            int s = sb.length();
            sb.append(link);
            sb.setSpan(new android.text.style.ForegroundColorSpan(getColor(R.color.zm_primary)), s, sb.length(), 0);
            v.setText(sb);
            ClickThrottle.setOnClickListener(v, x -> vm.openMedication());
        }
        addKvView(body, key, v);
    }

    private void addKvView(@NonNull LinearLayout body, @NonNull String key, @NonNull View value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView k = new TextView(this);
        k.setText(key);
        k.setTextAppearance(R.style.TextAppearance_Zmt_BodyMedium);
        k.setTextColor(getColor(R.color.zm_on_surface_variant));
        int w = Math.round(72 * getResources().getDisplayMetrics().density);
        row.addView(k, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(value, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.base_space_3);
        body.addView(row, lp);
    }

    /** 底部操作栏：结案无；待诊断“重新诊断”；所有者、场长“登记 / 修改处理结果”；诊断中无 */
    private void renderActions() {
        DiagnosisDetailVO d = vm.getDetail().getValue();
        Integer state = vm.getAiCardState().getValue();
        if (d == null || d.isClosed()) {
            binding.inspectActionBar.setVisibility(View.GONE);
            return;
        }
        DiagnosisDetailVO.DiagnosisPermissions p = d.getPermissions();
        String ai = d.getAiState();
        boolean unavailable = state != null && (state == DiagnosisDetailViewModel.AI_UNAVAILABLE
                || state == DiagnosisDetailViewModel.AI_FAILED);
        boolean diagnosing = state != null && state == DiagnosisDetailViewModel.AI_PLACEHOLDER;
        if (unavailable && p.isCanRediagnose() && !DiagnosisDetailVO.AI_DONE.equals(ai)) {
            binding.inspectActionBar.setPrimary(getString(com.zhimu.slmp.inspect.R.string.inspect_diag_rediagnose),
                    R.drawable.ic_refresh);
            binding.inspectActionBar.setOnPrimaryClickListener(v -> vm.rediagnose());
            binding.inspectActionBar.setPrimaryLoading(Boolean.TRUE.equals(vm.getRediagnosing().getValue()));
            binding.inspectActionBar.setVisibility(View.VISIBLE);
        } else if (!diagnosing && p.isCanHandle() && d.getHandle() == null) {
            binding.inspectActionBar.setPrimary(getString(com.zhimu.slmp.inspect.R.string.inspect_diag_handle),
                    R.drawable.ic_edit_note);
            binding.inspectActionBar.setOnPrimaryClickListener(v -> vm.openHandle());
            binding.inspectActionBar.setPrimaryLoading(false);
            binding.inspectActionBar.setVisibility(View.VISIBLE);
        } else if (!diagnosing && d.getHandle() != null && p.isCanModifyHandle()) {
            binding.inspectActionBar.setPrimary(getString(com.zhimu.slmp.inspect.R.string.inspect_diag_modify_handle),
                    R.drawable.ic_edit_note);
            binding.inspectActionBar.setOnPrimaryClickListener(v -> vm.openHandle());
            binding.inspectActionBar.setPrimaryLoading(false);
            binding.inspectActionBar.setVisibility(View.VISIBLE);
        } else {
            binding.inspectActionBar.setVisibility(View.GONE);
        }
    }

    private void showMore(@Nullable View anchor) {
        DiagnosisDetailVO d = vm.getDetail().getValue();
        if (anchor == null || d == null) {
            return;
        }
        MoreMenuHelper.Builder menu = MoreMenuHelper.with(anchor);
        DiagnosisDetailVO.DiagnosisPermissions p = d.getPermissions();
        if (p.isCanViewInspection() && d.getReport() != null && d.getReport().getInspectionId() != null) {
            menu.add(MENU_INSPECTION, getString(com.zhimu.slmp.inspect.R.string.inspect_diag_menu_inspection),
                    R.drawable.ic_fact_check);
        }
        if (p.isCanViewRevisions() && d.getHandle() != null && d.getHandle().isHasRevisions()) {
            menu.add(MENU_LOGS, getString(com.zhimu.slmp.inspect.R.string.inspect_diag_menu_logs), R.drawable.ic_history);
        }
        menu.setOnItemClickListener(id -> {
            if (id == MENU_INSPECTION) {
                vm.openInspection();
            } else if (id == MENU_LOGS) {
                vm.loadRecordLogs();
            }
        }).show();
    }
}
