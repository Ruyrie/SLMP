package com.zhimu.slmp.media.debug;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;

import com.zhimu.slmp.base.mvvm.BaseViewModel;
import com.zhimu.slmp.base.widget.StatusTagView;
import com.zhimu.slmp.media.MediaModuleEntry;
import com.zhimu.slmp.media.data.MediaScene;
import com.zhimu.slmp.media.di.MediaInjector;
import com.zhimu.slmp.media.upload.UploadItem;
import com.zhimu.slmp.media.upload.UploadManager;

import java.util.Arrays;

/**
 * 照片网格演示（只在 debug 包中）：宿主 ViewModel 的写法示例——持有 UploadManager、编辑模式带入已有文件、提交前检查。
 */
public class MediaDemoViewModel extends BaseViewModel {

    private UploadManager qualFiles;

    @Override
    protected void onSavedStateAttached(@NonNull SavedStateHandle handle) {
        qualFiles = MediaInjector.uploadFactory().create(handle, "demo_qual_files", MediaScene.FARM_QUAL, 9);
        qualFiles.setNewItemTag("新增", StatusTagView.PRIMARY);
        String base = MediaModuleEntry.MOCK_IMAGE_BASE;
        qualFiles.setInitialItems(Arrays.asList(
                UploadItem.remote(9001, base + "qual_license.jpg", base + "qual_license.jpg"),
                UploadItem.remote(9002, base + "qual_epidemic.jpg", base + "qual_epidemic.jpg")
                        .withTag("需重拍", StatusTagView.WARNING)));
    }

    public UploadManager getQualFiles() {
        return qualFiles;
    }

    /** 提交前检查（与业务页面相同的规则） */
    public void submit() {
        if (qualFiles.hasUploading()) {
            showMessage("资质图还在上传，请稍候");
            return;
        }
        String block = qualFiles.getSubmitBlockMessage();
        if (block != null) {
            showMessage(block);
            return;
        }
        if (qualFiles.getFileIds().isEmpty()) {
            showMessage("请至少保留 1 张资质图");
            return;
        }
        showMessage("qualification_file_ids = " + qualFiles.getFileIds());
    }

    @Override
    protected void onCleared() {
        qualFiles.release();
        super.onCleared();
    }
}
