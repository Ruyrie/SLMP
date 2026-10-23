package com.zhimu.slmp.ai.ui.course;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.CombinedLoadStates;
import androidx.paging.LoadState;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.zhimu.slmp.ai.AiExtras;
import com.zhimu.slmp.ai.R;
import com.zhimu.slmp.ai.databinding.AiFragmentCourseBinding;
import com.zhimu.slmp.ai.databinding.AiViewCourseHeaderBinding;
import com.zhimu.slmp.ai.di.AiInjector;
import com.zhimu.slmp.ai.model.PlayArgs;
import com.zhimu.slmp.ai.model.vo.ContinueWatchingVO;
import com.zhimu.slmp.ai.model.vo.CourseCategoryVO;
import com.zhimu.slmp.ai.ui.common.FooterAdapter;
import com.zhimu.slmp.ai.ui.common.SingleViewAdapter;
import com.zhimu.slmp.ai.util.AiText;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.ui.BaseFragment;
import com.zhimu.slmp.common.router.Router;
import com.zhimu.slmp.common.router.RouterPath;

import java.util.List;

import kotlin.Unit;

/**
 * K04 养殖课堂（宿主 AssistantFragment）：继续观看卡、专题（横向 / 全部专题纵向）、分类 Chip、两列视频卡分页、
 * 下拉刷新、搜索（结果沿用两列视频卡）。学习进度变化后更新对应卡片与继续观看卡。
 */
public class CourseHomeFragment extends BaseFragment {

    private AiFragmentCourseBinding binding;
    private AiViewCourseHeaderBinding header;
    private CourseHomeViewModel vm;
    private TopicCardAdapter topicAdapter;
    private CoursePagingAdapter courseAdapter;
    private SingleViewAdapter headerAdapter;
    private boolean sawLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AiFragmentCourseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = obtainViewModel(AiInjector.factory(), CourseHomeViewModel.class);
        observeBase(vm);
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        binding.aiCourseList.setLayoutManager(glm);
        header = AiViewCourseHeaderBinding.inflate(getLayoutInflater(), binding.aiCourseList, false);
        headerAdapter = new SingleViewAdapter(header.getRoot());
        courseAdapter = new CoursePagingAdapter(vm::onCourseClick, vm::overrideOf, vm::cardSub);
        FooterAdapter footer = new FooterAdapter(courseAdapter::retry);
        ConcatAdapter concat = new ConcatAdapter(headerAdapter, courseAdapter.withLoadStateFooter(footer));
        binding.aiCourseList.setAdapter(concat);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int head = headerAdapter.getItemCount();
                if (position < head) {
                    return 2;
                }
                return position - head < courseAdapter.getItemCount() ? 1 : 2;
            }
        });
        int gap = Math.round(6 * getResources().getDisplayMetrics().density);
        binding.aiCourseList.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect out, @NonNull View child, @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams) child.getLayoutParams();
                if (lp.getSpanSize() == 1) {
                    boolean left = lp.getSpanIndex() == 0;
                    out.set(left ? 0 : gap, 0, left ? gap : 0, 0);
                }
            }
        });

        topicAdapter = new TopicCardAdapter(vm::onTopicClick);
        header.aiHeadTopics.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL,
                false));
        header.aiHeadTopics.setAdapter(topicAdapter);
        header.aiHeadAllTopics.setOnClickListener(v -> vm.toggleTopics());
        header.aiHeadContinue.setOnClickListener(v -> vm.onContinueClick());
        header.aiHeadContinuePlay.setOnClickListener(v -> vm.onContinueClick());

        binding.aiCourseRefresh.setOnRefreshListener(vm::refresh);
        binding.aiCourseRefresh.setOnChildScrollUpCallback((p, c) -> binding.aiCourseList.canScrollVertically(-1));
        binding.aiCourseState.setOnRetryListener(v -> vm.retry());
        binding.aiCourseSearchBack.setOnClickListener(v -> vm.setSearching(false));
        binding.aiCourseSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (Boolean.TRUE.equals(vm.getSearching().getValue())) {
                    vm.onKeywordChanged(s.toString());
                }
            }
        });

        androidx.lifecycle.LifecycleOwner owner = getViewLifecycleOwner();
        vm.getUiState().observe(owner, s -> binding.aiCourseState.render(s));
        vm.getRefreshing().observe(owner, r -> {
            if (!Boolean.TRUE.equals(r)) {
                binding.aiCourseRefresh.setRefreshing(false);
            }
        });
        vm.getContinueWatching().observe(owner, this::renderContinue);
        vm.getTopics().observe(owner, list -> topicAdapter.submitList(list));
        vm.getTopicsExpanded().observe(owner, e -> {
            boolean on = Boolean.TRUE.equals(e);
            header.aiHeadTopics.setLayoutManager(new LinearLayoutManager(requireContext(),
                    on ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL, false));
            topicAdapter.setExpanded(on);
            header.aiHeadAllTopics.setText(on ? R.string.ai_course_collapse : R.string.ai_course_all_topics);
        });
        vm.getCategories().observe(owner, this::renderCategories);
        vm.getCourses().observe(owner, data -> courseAdapter.submitData(getLifecycle(), data));
        courseAdapter.addLoadStateListener(this::onLoadStates);
        vm.getProgressOverrides().observe(owner, map -> {
            if (map != null) {
                for (Long id : map.keySet()) {
                    courseAdapter.refreshCourse(id);
                }
            }
        });
        vm.getSearching().observe(owner, on -> {
            boolean s = Boolean.TRUE.equals(on);
            binding.aiCourseSearchBar.setVisibility(s ? View.VISIBLE : View.GONE);
            headerAdapter.setVisible(!s);
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (s) {
                binding.aiCourseSearchInput.requestFocus();
                if (imm != null) {
                    imm.showSoftInput(binding.aiCourseSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            } else {
                binding.aiCourseSearchInput.setText("");
                if (imm != null) {
                    imm.hideSoftInputFromWindow(binding.aiCourseSearchInput.getWindowToken(), 0);
                }
            }
        });
        vm.getOpenPlay().observe(owner, this::openPlay);
        vm.getOpenTopic().observe(owner, id -> {
            Bundle b = new Bundle();
            b.putLong(AiExtras.EXTRA_TOPIC_ID, id);
            Router.open(requireContext(), RouterPath.K07, b);
        });
        if (!isHidden()) {
            vm.start();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && vm != null) {
            vm.start();
        }
    }

    /** 顶部 search 图标 */
    public void toggleSearch() {
        if (vm != null) {
            vm.setSearching(!Boolean.TRUE.equals(vm.getSearching().getValue()));
        }
    }

    private void renderContinue(@Nullable ContinueWatchingVO c) {
        header.aiHeadContinue.setVisibility(c == null ? View.GONE : View.VISIBLE);
        if (c == null) {
            return;
        }
        header.aiHeadContinueTitle.setText(c.getTitle());
        header.aiHeadContinueSub.setText(AiText.continueText(c));
        int p = c.getProgressRatio() == null ? 0 : Math.round(c.getProgressRatio().floatValue() * 100);
        header.aiHeadContinueProgress.setProgress(p);
        Glide.with(header.aiHeadContinueCover).load(c.getCoverUrl()).centerCrop().into(header.aiHeadContinueCover);
    }

    private void renderCategories(@Nullable List<CourseCategoryVO> list) {
        header.aiHeadCategories.removeAllViews();
        if (list == null) {
            return;
        }
        Long selected = vm.getSelectedCategoryId().getValue();
        LayoutInflater inf = getLayoutInflater();
        for (CourseCategoryVO c : list) {
            Chip chip = (Chip) inf.inflate(R.layout.ai_item_filter_chip, header.aiHeadCategories, false);
            chip.setId(View.generateViewId());
            chip.setText(c.getName());
            chip.setCheckable(true);
            boolean on = c.getId() == 0 ? selected == null : selected != null && selected == c.getId();
            header.aiHeadCategories.addView(chip);
            chip.setChecked(on);
            chip.setOnClickListener(v -> {
                vm.selectCategory(c.getId());
                binding.aiCourseList.scrollToPosition(0);
            });
        }
    }

    private Unit onLoadStates(@NonNull CombinedLoadStates states) {
        if (binding == null) {
            return Unit.INSTANCE;
        }
        LoadState refresh = states.getRefresh();
        if (refresh instanceof LoadState.Loading) {
            sawLoading = true;
        } else {
            binding.aiCourseRefresh.setRefreshing(false);
        }
        boolean empty = courseAdapter.getItemCount() == 0 && refresh instanceof LoadState.NotLoading && sawLoading;
        header.aiHeadEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            header.aiHeadEmpty.setText(vm.emptyText());
        }
        if (Boolean.TRUE.equals(vm.getSearching().getValue())) {
            headerAdapter.setVisible(empty);
            header.aiHeadContinue.setVisibility(View.GONE);
            header.aiHeadTopicsRow.setVisibility(View.GONE);
            header.aiHeadTopics.setVisibility(View.GONE);
            ((View) header.aiHeadCategories.getParent()).setVisibility(View.GONE);
        } else {
            header.aiHeadTopicsRow.setVisibility(View.VISIBLE);
            header.aiHeadTopics.setVisibility(View.VISIBLE);
            ((View) header.aiHeadCategories.getParent()).setVisibility(View.VISIBLE);
            renderContinue(vm.getContinueWatching().getValue());
        }
        if (refresh instanceof LoadState.Error && courseAdapter.getItemCount() > 0) {
            ApiException e = ApiException.from(((LoadState.Error) refresh).getError());
            showMessage(e.isNetwork() ? ApiException.MSG_NETWORK : String.valueOf(e.getMessage()));
        }
        return Unit.INSTANCE;
    }

    private void openPlay(@NonNull PlayArgs args) {
        Bundle b = new Bundle();
        b.putLong(AiExtras.EXTRA_COURSE_ID, args.getCourseId());
        b.putString(AiExtras.EXTRA_ENTRY_TYPE, args.getEntryType());
        Router.open(requireContext(), RouterPath.K05, b);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
