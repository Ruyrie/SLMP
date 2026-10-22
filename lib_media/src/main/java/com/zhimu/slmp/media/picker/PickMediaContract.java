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
 * 系统照片选择器，选照片和视频（模块07 文档 2.7.2）：剩余 ≥ 2 用多选并限制数量，剩余 1 用单选；
 * 已有视频时只选图片；不申请存储权限。选完由 MediaQueueController 校验类型与时长（30 秒内）、只保留第一段视频。
 */
public class PickMediaContract extends ActivityResultContract<PickMediaContract.Input, List<Uri>> {

    /** 本次最多可选几个、是否允许选视频 */
    public static final class Input {
        final int maxItems;
        final boolean allowVideo;

        public Input(int maxItems, boolean allowVideo) {
            this.maxItems = Math.max(1, maxItems);
            this.allowVideo = allowVideo;
        }
    }

    private final ActivityResultContracts.PickMultipleVisualMedia parser =
            new ActivityResultContracts.PickMultipleVisualMedia();

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull Input input) {
        PickVisualMediaRequest.Builder builder = new PickVisualMediaRequest.Builder()
                .setMediaType(input.allowVideo ? ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE
                        : ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE);
        if (input.maxItems == 1) {
            return new ActivityResultContracts.PickVisualMedia().createIntent(context, builder.build());
        }
        builder.setMaxItems(input.maxItems);
        return new ActivityResultContracts.PickMultipleVisualMedia(input.maxItems).createIntent(context, builder.build());
    }

    @NonNull
    @Override
    public List<Uri> parseResult(int resultCode, @Nullable Intent intent) {
        return parser.parseResult(resultCode, intent);
    }
}
