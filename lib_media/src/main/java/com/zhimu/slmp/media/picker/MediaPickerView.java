package com.zhimu.slmp.media.picker;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.zhimu.slmp.base.dialog.OptionSheetDialogFragment;
import com.zhimu.slmp.base.mvvm.UiMessage;
import com.zhimu.slmp.base.ui.SnackbarHelper;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.media.R;
import com.zhimu.slmp.media.camera.CameraArgs;
import com.zhimu.slmp.media.camera.CameraContract;
import com.zhimu.slmp.media.camera.CameraResult;
import com.zhimu.slmp.media.ui.viewer.MediaViewer;
import com.zhimu.slmp.media.ui.viewer.ViewerItem;
import com.zhimu.slmp.media.upload.UploadController;
import com.zhimu.slmp.media.upload.UploadItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 照片网格（02 号规范 7.15 节）：三列 1:1 缩略图（圆角 12 dp、间距 8 dp），最后一格“添加”（surface-container-high），
 * 上传中 32% 黑色遮罩 + 环形进度 + 百分比，失败格 error-container“上传失败，点按重试”，右上角删除按钮（inverse-surface），
 * 左下角可带“新增”“需重拍”标签；已满时不显示“添加”格。点“添加”弹出“拍照 / 从相册选择”底部面板
 * （OptionSheetDialogFragment，结果经 FragmentResult 回传），拍照打开 I08、相册用系统照片选择器（不申请存储权限），
 * 点缩略图打开 I09 全屏预览。区块标题、计数与限制说明由宿主布局提供（如“照片”“选填 · JPG 或 PNG，单张不超过 5MB”）。
 *
 * <h3>宿主接入（表单页，可编辑）</h3>
 * <ol>
 *     <li>布局：{@code <com.zhimu.slmp.media.picker.MediaPickerView android:id="@+id/photos" app:mediaCameraTitle="死淘登记"
 *     app:mediaShowCount="true" android:layout_width="match_parent" android:layout_height="wrap_content" />}（必须有 id）</li>
 *     <li>ViewModel：构造方法注入 {@code UploadManager.Factory uploads}（依赖都来自 CoreComponent，业务组件不用加 Module）；
 *     在 {@code onSavedStateAttached(h)} 中 {@code photos = uploads.create(h, "photos", MediaScene.DEATH, 6)}，
 *     对外提供 {@code getPhotos()}；{@code onCleared()} 中调用 {@code photos.release()}</li>
 *     <li>页面：在 Activity.onCreate 或 Fragment.onViewCreated 中调用 {@code binding.photos.bind(this, vm.getPhotos())}
 *     （内部注册 Activity Result，必须在页面 STARTED 之前调用）</li>
 *     <li>编辑模式：加载到记录后 {@code photos.setInitialFiles(vo.getPhotos())}（只生效一次，可先 withTag 加“需重拍”）；
 *     F06 新图加“新增”：{@code photos.setNewItemTag("新增", StatusTagView.PRIMARY)}</li>
 *     <li>提交：{@code photos.hasUploading()} 为真时主按钮进入加载态等待（观察 getItems 变化后再提交）；
 *     {@code photos.getSubmitBlockMessage()} 非空时提示并不提交；请求体用 {@code photos.getFileIds()}（按网格顺序）</li>
 * </ol>
 * 页面重建：网格内容保存在宿主 ViewModel 的 SavedStateHandle 中（键为 create 的第二个参数），配置变化时上传不中断，
 * 进程被回收后未完成的项自动重新上传。
 *
 * <h3>只读（详情页）</h3>
 * 布局加 {@code app:mediaReadOnly="true"}，代码中 {@code binding.photos.setReadOnlyFiles(vo.getPhotos(), "死淘登记")}，
 * 不需要 bind；点缩略图打开全屏预览（MediaItemVO 的来源、拍摄人、拍摄时间显示为两行说明）。
 */
public class MediaPickerView extends FrameLayout {

    private static final String SOURCE_CAMERA = "camera";
    private static final String SOURCE_VIDEO = "video";
    private static final String SOURCE_ALBUM = "album";
    private static final String ACTION_UNDO = "media_undo";
    private static final int SPAN = 3;

    private final RecyclerView recycler;
    private final MediaGridAdapter adapter;

    private int maxCount;
    private boolean readOnly;
    private boolean showCount;
    @Nullable
    private String sheetTitle;
    @Nullable
    private String cameraTitle;
    @Nullable
    private String sourceText;
    @NonNull
    private String deletedText;

    @Nullable
    private UploadController controller;
    @NonNull
    private List<UploadItem> items = Collections.emptyList();
    /** 只读模式下的原始数据（全屏预览用） */
    @NonNull
    private List<? extends FileVO> readOnlyFiles = Collections.emptyList();

    @Nullable
    private ActivityResultLauncher<CameraArgs> cameraLauncher;
    @Nullable
    private ActivityResultLauncher<Integer> pickLauncher;
    @Nullable
    private ActivityResultLauncher<PickMediaContract.Input> pickMediaLauncher;
    @Nullable
    private ActivityResultLauncher<MediaViewer.Input> viewerLauncher;
    @Nullable
    private FragmentManager fragmentManager;
    @Nullable
    private String requestKeyBase;

    public MediaPickerView(@NonNull Context context) {
        this(context, null);
    }

    public MediaPickerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MediaPickerView);
        maxCount = a.getInt(R.styleable.MediaPickerView_mediaMaxCount, 9);
        readOnly = a.getBoolean(R.styleable.MediaPickerView_mediaReadOnly, false);
        showCount = a.getBoolean(R.styleable.MediaPickerView_mediaShowCount, false);
        int addIcon = a.getResourceId(R.styleable.MediaPickerView_mediaAddIcon,
                com.zhimu.slmp.base.R.drawable.ic_add_a_photo);
        sheetTitle = a.getString(R.styleable.MediaPickerView_mediaSheetTitle);
        cameraTitle = a.getString(R.styleable.MediaPickerView_mediaCameraTitle);
        sourceText = a.getString(R.styleable.MediaPickerView_mediaSourceText);
        String deleted = a.getString(R.styleable.MediaPickerView_mediaDeletedText);
        a.recycle();
        deletedText = deleted != null ? deleted : context.getString(R.string.media_picker_deleted);
        if (sheetTitle == null) {
            sheetTitle = context.getString(R.string.media_picker_sheet_title);
        }

        adapter = new MediaGridAdapter(new MediaGridAdapter.Callback() {
            @Override
            public void onAddClick() {
                showSourceSheet();
            }

            @Override
            public void onItemClick(@NonNull UploadItem item) {
                openViewer(item);
            }

            @Override
            public void onRetryClick(@NonNull UploadItem item) {
                if (controller != null) {
                    controller.retry(item.getId());
                }
            }

            @Override
            public void onDeleteClick(@NonNull UploadItem item) {
                removeItem(item);
            }
        });
        adapter.setAddIcon(addIcon);

        recycler = new RecyclerView(context);
        recycler.setLayoutManager(new GridLayoutManager(context, SPAN));
        recycler.setAdapter(adapter);
        recycler.setNestedScrollingEnabled(false);
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        recycler.addItemDecoration(new GridGap(getResources().getDimensionPixelSize(R.dimen.media_grid_gap)));
        RecyclerView.ItemAnimator animator = recycler.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        addView(recycler, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        render();
    }

    // ===== 绑定 =====

    /** Activity 宿主：在 onCreate 中调用 */
    @MainThread
    public void bind(@NonNull FragmentActivity host, @NonNull UploadController controller) {
        bindInternal(host, host, host.getSupportFragmentManager(), controller, host.getClass().getName());
    }

    /** Fragment 宿主：在 onViewCreated 中调用（观察者绑定到 getViewLifecycleOwner） */
    @MainThread
    public void bind(@NonNull Fragment host, @NonNull UploadController controller) {
        bindInternal(host.requireActivity(), host.getViewLifecycleOwner(), host.getChildFragmentManager(), controller,
                host.getClass().getName());
    }

    private void bindInternal(@NonNull ComponentActivity activity, @NonNull LifecycleOwner owner,
                              @NonNull FragmentManager fm, @NonNull UploadController c, @NonNull String scope) {
        if (getId() == View.NO_ID) {
            throw new IllegalStateException("MediaPickerView 需要设置 android:id（用作结果键）");
        }
        this.controller = c;
        this.readOnly = false;
        this.maxCount = c.getMaxCount();
        this.fragmentManager = fm;
        String base = "media_picker:" + scope + ":" + getId();
        requestKeyBase = base;

        // 结果回调只引用 controller（随宿主 ViewModel 存活），不引用本视图
        ActivityResultRegistry registry = activity.getActivityResultRegistry();
        cameraLauncher = registry.register(base + ":camera", owner, new CameraContract(), result -> {
            // 上传队列模式（TYPE_QUEUED）由相机页直接写入 upload_task，网格观察数据库自动更新
            if (result.getType() == CameraResult.TYPE_PHOTOS) {
                c.addCapturedFiles(result.getPhotoPaths());
            }
        });
        pickMediaLauncher = registry.register(base + ":pick_media", owner, new PickMediaContract(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                c.addUris(new ArrayList<>(uris));
            }
        });
        // 从表单预览：关闭时回传被删除项的 key
        viewerLauncher = registry.register(base + ":viewer", owner, new MediaViewer.Contract(), keys -> {
            for (String key : keys) {
                c.remove(key);
            }
        });
        pickLauncher = registry.register(base + ":pick", owner, new PickImagesContract(), uris -> {
            if (uris != null && !uris.isEmpty()) {
                c.addUris(new ArrayList<>(uris));
            }
        });
        fm.setFragmentResultListener(base + ":source", owner,
                (key, result) -> onSourcePicked(OptionSheetDialogFragment.getValue(result)));

        c.getItems().observe(owner, list -> {
            items = list == null ? Collections.emptyList() : list;
            render();
        });
        c.getMessage().observe(owner, this::showMessage);
    }

    /** 只读展示服务端文件（详情页）；sourceText 为全屏预览的来源说明 */
    public void setReadOnlyFiles(@Nullable List<? extends FileVO> files, @Nullable String sourceText) {
        readOnly = true;
        controller = null;
        this.sourceText = sourceText;
        readOnlyFiles = files == null ? Collections.emptyList() : files;
        List<UploadItem> list = new ArrayList<>(readOnlyFiles.size());
        for (FileVO f : readOnlyFiles) {
            list.add(UploadItem.remote(f));
        }
        items = list;
        render();
    }

    // ===== 宿主查询（转发给 controller） =====

    /** 已上传完成的 file_id（按网格顺序）；只读模式返回展示的文件 */
    @NonNull
    public List<Long> getFileIds() {
        if (controller != null) {
            return controller.getFileIds();
        }
        List<Long> ids = new ArrayList<>();
        for (UploadItem item : items) {
            if (item.getFileId() > 0) {
                ids.add(item.getFileId());
            }
        }
        return ids;
    }

    public boolean hasUploading() {
        return controller != null && controller.hasUploading();
    }

    public boolean hasFailed() {
        return controller != null && controller.hasFailed();
    }

    /** 计数“2 / 9”（宿主放在区块标题右侧时使用） */
    @NonNull
    public String getCountText() {
        return String.format(Locale.ROOT, "%d / %d", items.size(), maxCount);
    }

    // ===== 外观 =====

    public void setCameraTitle(@Nullable String title) {
        this.cameraTitle = title;
    }

    public void setSourceText(@Nullable String text) {
        this.sourceText = text;
    }

    public void setShowCount(boolean show) {
        this.showCount = show;
        render();
    }

    public void setAddIcon(@DrawableRes int icon) {
        adapter.setAddIcon(icon);
        adapter.notifyDataSetChanged();
    }

    /** 未绑定 controller 时的最多张数（只读或预览） */
    public void setMaxCount(int max) {
        if (controller == null) {
            this.maxCount = Math.max(1, max);
            render();
        }
    }

    // ===== 内部 =====

    private void render() {
        List<MediaGridAdapter.Cell> cells = new ArrayList<>(items.size() + 1);
        for (UploadItem item : items) {
            cells.add(MediaGridAdapter.Cell.media(item, readOnly));
        }
        if (!readOnly && items.size() < maxCount) {
            String count = showCount ? getContext().getString(R.string.media_picker_add_count, items.size(), maxCount)
                    : null;
            cells.add(MediaGridAdapter.Cell.add(count));
        }
        adapter.submitList(cells);
    }

    private void showSourceSheet() {
        UploadController c = controller;
        FragmentManager fm = fragmentManager;
        if (c == null || fm == null || requestKeyBase == null) {
            return;
        }
        if (c.getRemaining() <= 0) {
            showMessage(UiMessage.of(getContext().getString(R.string.media_picker_full, c.getMaxCount())));
            return;
        }
        OptionSheetDialogFragment.Builder sheet = OptionSheetDialogFragment.builder(requestKeyBase + ":source")
                .option(SOURCE_CAMERA, getContext().getString(R.string.media_picker_take_photo), null);
        if (c.getMaxVideoCount() > 0) {
            boolean has = c.hasVideo();
            sheet.title(sheetTitle != null && !sheetTitle.equals(getContext().getString(R.string.media_picker_sheet_title))
                    ? sheetTitle : getContext().getString(R.string.media_picker_sheet_title_media));
            sheet.option(new OptionSheetDialogFragment.Option(SOURCE_VIDEO,
                    getContext().getString(R.string.media_picker_record),
                    has ? getContext().getString(R.string.media_picker_has_video) : null).disabled(has));
        } else {
            sheet.title(sheetTitle);
        }
        sheet.option(SOURCE_ALBUM, getContext().getString(R.string.media_picker_from_album), null)
                .build()
                .showSafely(fm);
    }

    private void onSourcePicked(@Nullable String source) {
        UploadController c = controller;
        if (c == null) {
            return;
        }
        int remaining = c.getRemaining();
        if (remaining <= 0) {
            return;
        }
        String page = c.getQueuePageCode();
        if ((SOURCE_CAMERA.equals(source) || SOURCE_VIDEO.equals(source)) && cameraLauncher != null) {
            if (page != null) {
                // 照片和视频网格：相机页可切换拍照 / 录像，拍摄结果直接写入上传队列
                cameraLauncher.launch(CameraArgs.media(c.getScene(), remaining, page, c.getQueueFarmId())
                        .startWithVideo(SOURCE_VIDEO.equals(source))
                        .hasVideo(c.hasVideo() || c.getMaxVideoCount() <= 0)
                        .title(cameraTitle)
                        .counts(items.size(), c.getMaxCount()));
            } else {
                cameraLauncher.launch(CameraArgs.photo(c.getScene(), remaining)
                        .title(cameraTitle)
                        .counts(items.size(), c.getMaxCount()));
            }
        } else if (SOURCE_ALBUM.equals(source)) {
            if (c.getMaxVideoCount() > 0 && pickMediaLauncher != null) {
                pickMediaLauncher.launch(new PickMediaContract.Input(remaining, !c.hasVideo()));
            } else if (pickLauncher != null) {
                pickLauncher.launch(remaining);
            }
        }
    }

    private void removeItem(@NonNull UploadItem item) {
        UploadController c = controller;
        if (c == null) {
            return;
        }
        c.remove(item.getId());
        View host = snackbarHost();
        String text = item.isVideo() ? getContext().getString(R.string.media_picker_deleted_video) : deletedText;
        SnackbarHelper.show(host, UiMessage.withAction(text, getContext().getString(R.string.media_undo),
                ACTION_UNDO), SnackbarHelper.findAnchor(host), actionId -> c.undoRemove());
    }

    private void openViewer(@NonNull UploadItem clicked) {
        List<ViewerItem> viewerItems = new ArrayList<>(items.size());
        int start = 0;
        for (int i = 0; i < items.size(); i++) {
            UploadItem item = items.get(i);
            if (item.getId().equals(clicked.getId())) {
                start = i;
            }
            if (readOnly && i < readOnlyFiles.size()) {
                viewerItems.add(ViewerItem.from(readOnlyFiles.get(i), sourceText));
            } else {
                viewerItems.add(ViewerItem.from(item, sourceText));
            }
        }
        if (!readOnly && viewerLauncher != null && controller != null) {
            viewerLauncher.launch(new MediaViewer.Input(viewerItems, start, true));
        } else {
            MediaViewer.open(hostContext(), viewerItems, start);
        }
    }

    /** 优先用所在 Activity 打开页面（视图的 Context 可能是 ContextThemeWrapper） */
    @NonNull
    private Context hostContext() {
        Context ctx = getContext();
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                return ctx;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return getContext();
    }

    private void showMessage(@Nullable UiMessage message) {
        if (message == null) {
            return;
        }
        View host = snackbarHost();
        SnackbarHelper.show(host, message, SnackbarHelper.findAnchor(host), null);
    }

    @NonNull
    private View snackbarHost() {
        View content = getRootView().findViewById(android.R.id.content);
        return content != null ? content : this;
    }

    /** 三列等宽、间距 8 dp */
    private static final class GridGap extends RecyclerView.ItemDecoration {
        private final int gap;

        GridGap(int gap) {
            this.gap = gap;
        }

        @Override
        public void getItemOffsets(@NonNull Rect out, @NonNull View view, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            int column = position % SPAN;
            out.left = column * gap / SPAN;
            out.right = gap - (column + 1) * gap / SPAN;
            out.top = position >= SPAN ? gap : 0;
        }
    }
}
