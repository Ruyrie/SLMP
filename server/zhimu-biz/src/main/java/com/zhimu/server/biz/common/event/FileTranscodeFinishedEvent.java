package com.zhimu.server.biz.common.event;

/**
 * 视频转码结束（成功或重试后最终失败）。管理端-02 监听后回写课程转码状态、时长并提醒上传人。
 *
 * @param fileId 原视频 t_file.id
 * @param success 是否全部派生版本成功
 * @param reason 失败原因（成功时为空）
 */
public record FileTranscodeFinishedEvent(Long fileId, boolean success, String reason) {
}
