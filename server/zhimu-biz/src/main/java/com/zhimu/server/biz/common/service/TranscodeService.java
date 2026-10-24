package com.zhimu.server.biz.common.service;

/**
 * 课程视频转码（ADM-S36 合并后入队、ADM-S38 重试、TranscodeDispatchJob 补偿）。
 * 状态写入 t_file.transcode_status 与 t_file_variant；完成或最终失败时发布 FileTranscodeFinishedEvent。
 */
public interface TranscodeService {

    /** 提交到转码线程池；应在事务提交后调用（AfterCommit） */
    void submit(Long fileId);

    /** ADM-S38：只重置失败的派生版本并重新入队 */
    void retry(Long fileId);

    /** 每分钟补偿：重启后恢复 WAITING、按 1/5/15 分钟重试未成功的派生版本 */
    void dispatch();
}
