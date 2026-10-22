package com.zhimu.slmp.media.picker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 系统照片选择器，只选图片（03 号规范 8.3 节、模块07 文档 2.7.2）：输入为本次最多可选张数，
 * 剩余 1 张时用单选，2 张及以上用多选并限制数量；不申请存储权限。
 * <p>
 * 系统不支持照片选择器时回退为 ACTION_OPEN_DOCUMENT，此时数量限制可能不生效，由 UploadManager 截断。
 */
public class PickImagesContract extends ActivityResultContract<Integer, List<Uri>> {

    private final ActivityResultContracts.PickMultipleVisualMedia parser =
            new ActivityResultContracts.PickMultipleVisualMedia();

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull Integer maxItems) {
        int max = Math.max(1, maxItems);
        PickVisualMediaRequest.Builder builder = new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE);
        if (max == 1) {
            return new ActivityResultContracts.PickVisualMedia().createIntent(context, builder.build());
        }
        builder.setMaxItems(max);
        return new ActivityResultContracts.PickMultipleVisualMedia(max).createIntent(context, builder.build());
    }

    @NonNull
    @Override
    public List<Uri> parseResult(int resultCode, @Nullable Intent intent) {
        return parser.parseResult(resultCode, intent);
    }
}
