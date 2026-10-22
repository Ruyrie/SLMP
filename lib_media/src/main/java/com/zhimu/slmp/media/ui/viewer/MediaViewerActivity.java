package com.zhimu.slmp.media.ui.viewer;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import com.zhimu.slmp.base.ui.BaseActivity;
import com.zhimu.slmp.base.ui.InsetsHelper;
import com.zhimu.slmp.media.R;
import com.zhimu.slmp.media.databinding.MediaActivityViewerBinding;
import com.zhimu.slmp.media.di.MediaInjector;

import java.util.List;

/**
 * I09 照片和视频全屏预览（02 号规范 7.16、10.2 节）：ViewPager2 左右滑动、图片双指缩放与双击放大、序号“3 / 6”、
 * 两行来源说明、缩略图条、保存到相册（MediaStore，不申请存储权限）。单击画面显示或隐藏顶栏与底部说明。
 * 打开方式见 {@link MediaViewer}。
 */
public class MediaViewerActivity extends BaseActivity {

    private MediaActivityViewerBinding binding;
    private MediaViewerViewModel vm;
    private ViewerPageAdapter pageAdapter;
    private ViewerThumbAdapter thumbAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.media_activity_viewer);
        vm = obtainViewModel(MediaInjector.factory(), MediaViewerViewModel.class);
        binding.setVm(vm);
        binding.setLifecycleOwner(this);
        observeBase(vm);

        InsetsHelper.applyTopPadding(binding.mediaTopBar);
        InsetsHelper.applyBottomPadding(binding.mediaBottomPanel);

        pageAdapter = new ViewerPageAdapter(new ViewerPageAdapter.Callback() {
            @Override
            public void onPageTap() {
                vm.toggleChrome();
            }

            @Override
            public void onLoadFailed(int position) {
                vm.refreshUrl(position);
            }

            @Override
            public void onVideoTap() {
                showMessage(MediaViewerViewModel.MSG_VIDEO_UNSUPPORTED);
            }
        });
        binding.mediaPager.setAdapter(pageAdapter);
        binding.mediaPager.setOffscreenPageLimit(1);
        binding.mediaPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                vm.selectPage(position);
            }
        });

        thumbAdapter = new ViewerThumbAdapter(position -> binding.mediaPager.setCurrentItem(position, true));
        binding.mediaThumbs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.mediaThumbs.setAdapter(thumbAdapter);

        vm.getItems().observe(this, this::renderItems);
        vm.getCurrentIndex().observe(this, index -> {
            if (index == null) {
                return;
            }
            if (binding.mediaPager.getCurrentItem() != index) {
                binding.mediaPager.setCurrentItem(index, false);
            }
            thumbAdapter.setSelected(index);
            binding.mediaThumbs.scrollToPosition(index);
        });
        vm.getChromeVisible().observe(this, visible -> {
            boolean show = Boolean.TRUE.equals(visible);
            fade(binding.mediaTopBar, show);
            fade(binding.mediaBottomPanel, show);
        });
        vm.getClose().observe(this, v -> finish());
    }

    @Override
    protected boolean applyWindowInsetsToRoot() {
        // 图片铺满到状态栏和导航栏下方，顶栏与底部说明自行加 insets
        return false;
    }

    private void renderItems(@Nullable List<ViewerItem> items) {
        if (items == null) {
            return;
        }
        Integer index = vm.getCurrentIndex().getValue();
        pageAdapter.submitList(items, () -> {
            if (index != null && binding.mediaPager.getCurrentItem() != index) {
                binding.mediaPager.setCurrentItem(index, false);
            }
        });
        thumbAdapter.submit(items);
        if (index != null) {
            thumbAdapter.setSelected(index);
        }
        binding.mediaThumbs.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private static void fade(View view, boolean show) {
        view.animate().cancel();
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(150).start();
        } else {
            view.animate().alpha(0f).setDuration(150).withEndAction(() -> view.setVisibility(View.INVISIBLE)).start();
        }
    }
}
