package com.zhimu.server.integration.media;

import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import lombok.Getter;

/**
 * 音视频、图片处理失败（50001）。detail 为 ffmpeg 错误输出的最后若干行等排查信息，可写入转码失败原因，不要直接展示给用户。
 */
@Getter
public class MediaException extends BizException {

    private final String detail;

    public MediaException(String message, String detail) {
        super(ErrorCode.SERVER_ERROR, message);
        this.detail = detail;
    }
}
