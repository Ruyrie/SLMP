package com.zhimu.slmp.feed.ui.plan;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.zhimu.slmp.base.util.ClickThrottle;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.feed.R;
import com.zhimu.slmp.feed.databinding.FeedItemStageBinding;
import com.zhimu.slmp.feed.model.vo.FeedOptionBriefVO;
import com.zhimu.slmp.feed.ui.common.FeedViews;
import com.zhimu.slmp.feed.ui.common.OptionAdapter;
import com.zhimu.slmp.feed.util.FeedFormat;
import com.zhimu.slmp.feed.util.FeedPlanValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * G03 阶段卡列表：阶段数很少（3–5 个）且卡内有输入框，直接在 LinearLayout 中复用卡片视图，submit 时按下标局部刷新
 * （输入框内容相同时不回写，避免光标跳动）。同一时间只展开一张卡，展开 / 收起 300 ms 过渡。
 */
public class FeedPlanStageAdapter {

    /** 卡片内操作回调（参数为阶段下标） */
    public interface Listener {
        void onExpand(int index);

        void onFeedSelected(int index, long feedId);

        void onDailyQtyChanged(int index, @NonNull String text);

        void onMealsSelected(int index, int meals);

        void onTimeClick(int index, int mealIndex);
    }

    private final LinearLayout container;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final List<Holder> holders = new ArrayList<>();
    private int lastExpanded = -2;

    private static final class Holder {
        final FeedItemStageBinding b;
        final OptionAdapter feeds;
        final OptionAdapter meals;
        int index;
        boolean binding;

        Holder(@NonNull FeedItemStageBinding b, @NonNull OptionAdapter feeds, @NonNull OptionAdapter meals) {
            this.b = b;
            this.feeds = feeds;
            this.meals = meals;
        }
    }

    public FeedPlanStageAdapter(@NonNull LinearLayout container, @NonNull Listener listener) {
        this.container = container;
        this.inflater = LayoutInflater.from(container.getContext());
        this.listener = listener;
    }

    /** 第 index 张卡（滚动定位用） */
    @Nullable
    public View viewAt(int index) {
        return index >= 0 && index < holders.size() ? holders.get(index).b.getRoot() : null;
    }

    public void submit(@Nullable List<FeedStageForm> stages, int expanded, @Nullable Map<String, String> errors,
                       @Nullable List<FeedOptionBriefVO> feedOptions, @Nullable Integer currentCount) {
        List<FeedStageForm> list = stages == null ? Collections.emptyList() : stages;
        if (expanded != lastExpanded && lastExpanded != -2) {
            AutoTransition t = new AutoTransition();
            t.setDuration(300);
            t.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            TransitionManager.beginDelayedTransition(container, t);
        }
        lastExpanded = expanded;
        while (holders.size() < list.size()) {
            holders.add(create());
        }
        while (holders.size() > list.size()) {
            Holder h = holders.remove(holders.size() - 1);
            container.removeView(h.b.getRoot());
        }
        for (int i = 0; i < list.size(); i++) {
            bind(holders.get(i), i, list.get(i), i == expanded, errors, feedOptions, currentCount);
        }
    }

    @NonNull
    private Holder create() {
        FeedItemStageBinding b = FeedItemStageBinding.inflate(inflater, container, false);
        OptionAdapter feeds = FeedViews.bindDropdown(b.feedStageFeedInput, id -> {
        });
        OptionAdapter meals = FeedViews.bindDropdown(b.feedStageMealsInput, id -> {
        });
        Holder h = new Holder(b, feeds, meals);
        b.feedStageFeedInput.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = feeds.getItem(position);
            if (item != null && !item.disabled) {
                listener.onFeedSelected(h.index, item.id);
            }
        });
        b.feedStageMealsInput.setOnItemClickListener((p, v, position, id) -> {
            OptionAdapter.Item item = meals.getItem(position);
            if (item != null) {
                listener.onMealsSelected(h.index, (int) item.id);
            }
        });
        List<OptionAdapter.Item> mealItems = new ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            mealItems.add(new OptionAdapter.Item(n, "每天 " + n + " 餐", null, null, false));
        }
        meals.setItems(mealItems);
        b.feedStageQtyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!h.binding) {
                    listener.onDailyQtyChanged(h.index, s.toString());
                }
            }
        });
        container.addView(b.getRoot());
        return h;
    }

    private void bind(@NonNull Holder h, int index, @NonNull FeedStageForm s, boolean expanded,
                      @Nullable Map<String, String> errors, @Nullable List<FeedOptionBriefVO> feedOptions,
                      @Nullable Integer currentCount) {
        h.index = index;
        h.binding = true;
        FeedItemStageBinding b = h.b;
        b.feedStageName.setText(s.stageName);
        b.feedStageRange.setText(s.rangeText());
        if (s.isEnded()) {
            b.feedStageTag.setStatus(b.getRoot().getContext().getString(R.string.feed_tag_ended), StatusTagView.NEUTRAL);
            b.feedStageTag.setVisibility(View.VISIBLE);
        } else if (s.isCurrent()) {
            b.feedStageTag.setStatus(b.getRoot().getContext().getString(R.string.feed_plan_stage_current),
                    StatusTagView.PRIMARY);
            b.feedStageTag.setVisibility(View.VISIBLE);
        } else {
            b.feedStageTag.setVisibility(View.GONE);
        }
        boolean expandable = s.editable && !expanded;
        b.feedStageChevron.setVisibility(expandable && !s.isCurrent() ? View.VISIBLE : View.GONE);
        b.feedStageChevron.setImageResource(expanded ? com.zhimu.slmp.base.R.drawable.ic_expand_more
                : com.zhimu.slmp.base.R.drawable.ic_chevron_right);
        b.feedStageSummary.setVisibility(expanded ? View.GONE : View.VISIBLE);
        b.feedStageSummary.setText(s.summaryLine());
        String enter = s.enterLine();
        b.feedStageEnter.setVisibility(!expanded && enter != null ? View.VISIBLE : View.GONE);
        b.feedStageEnter.setText(enter);
        b.feedStageBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        b.feedStageCard.setClickable(expandable);
        if (expandable) {
            ClickThrottle.setOnClickListener(b.feedStageCard, v -> listener.onExpand(h.index));
        } else {
            b.feedStageCard.setOnClickListener(null);
            b.feedStageCard.setClickable(false);
        }

        if (expanded) {
            // 饲料（方案可选结存为 0 的饲料）
            List<OptionAdapter.Item> items = new ArrayList<>();
            if (feedOptions != null) {
                for (FeedOptionBriefVO o : feedOptions) {
                    items.add(new OptionAdapter.Item(o.getFeedId(), FeedFormat.orEmpty(o.getFeedName()),
                            "结存 " + FeedFormat.kg(o.getStockQty()),
                            o.isLowStock() ? b.getRoot().getContext().getString(R.string.feed_tag_low_stock) : null,
                            false));
                }
            }
            h.feeds.setItems(items);
            FeedViews.showSelected(b.feedStageFeedInput, h.feeds, s.feedId);
            if (s.feedId != null && h.feeds.findById(s.feedId) == null) {
                b.feedStageFeedInput.setText(FeedFormat.orEmpty(s.feedName), false);
            }
            b.feedStageLowStock.setVisibility(s.lowStock ? View.VISIBLE : View.GONE);
            setError(b.feedStageFeedLayout, errors, FeedPlanValidator.key(index, FeedPlanValidator.F_FEED));

            String current = b.feedStageQtyInput.getText() == null ? "" : b.feedStageQtyInput.getText().toString();
            if (!current.equals(s.dailyQtyText)) {
                b.feedStageQtyInput.setText(s.dailyQtyText);
            }
            b.feedStageQtyLayout.setHelperText(s.stdDailyQty == null ? null
                    : "品种标准 " + FeedFormat.kg(s.stdDailyQty));
            setError(b.feedStageQtyLayout, errors, FeedPlanValidator.key(index, FeedPlanValidator.F_DAILY_QTY));
            FeedViews.showSelected(b.feedStageMealsInput, h.meals, (long) s.mealsPerDay);
            setError(b.feedStageMealsLayout, errors, FeedPlanValidator.key(index, FeedPlanValidator.F_MEALS));

            bindTimes(h, s);
            String timeError = errors == null ? null
                    : errors.get(FeedPlanValidator.key(index, FeedPlanValidator.F_MEAL_TIMES));
            b.feedStageTimeError.setText(timeError);
            b.feedStageTimeError.setVisibility(TextUtils.isEmpty(timeError) ? View.GONE : View.VISIBLE);

            b.feedStageSumLabel.setText(s.summaryLabel(currentCount));
            String value = s.todayPlanText(currentCount);
            b.feedStageSumValue.setText(value);
            b.feedStageSumValue.setVisibility(value == null ? View.GONE : View.VISIBLE);
        }
        h.binding = false;
    }

    private void bindTimes(@NonNull Holder h, @NonNull FeedStageForm s) {
        FeedItemStageBinding b = h.b;
        while (b.feedStageTimes.getChildCount() > s.mealTimes.size()) {
            b.feedStageTimes.removeViewAt(b.feedStageTimes.getChildCount() - 1);
        }
        while (b.feedStageTimes.getChildCount() < s.mealTimes.size()) {
            Chip chip = (Chip) inflater.inflate(R.layout.feed_item_time_chip, b.feedStageTimes, false);
            b.feedStageTimes.addView(chip);
        }
        for (int i = 0; i < s.mealTimes.size(); i++) {
            Chip chip = (Chip) b.feedStageTimes.getChildAt(i);
            chip.setText(s.mealTimes.get(i));
            final int meal = i;
            ClickThrottle.setOnClickListener(chip, v -> listener.onTimeClick(h.index, meal));
        }
    }

    private static void setError(@NonNull com.google.android.material.textfield.TextInputLayout layout,
                                 @Nullable Map<String, String> errors, @NonNull String key) {
        String e = errors == null ? null : errors.get(key);
        if (!TextUtils.equals(layout.getError(), e)) {
            layout.setError(TextUtils.isEmpty(e) ? null : e);
            if (TextUtils.isEmpty(e)) {
                layout.setErrorEnabled(false);
            }
        }
    }
}
