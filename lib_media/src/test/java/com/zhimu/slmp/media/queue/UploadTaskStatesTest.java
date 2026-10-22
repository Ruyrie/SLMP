package com.zhimu.slmp.media.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.media.upload.UploadItem;

import org.junit.Test;

/**
 * upload_task 状态变化、失败重试决策与进度节流。
 */
public class UploadTaskStatesTest {

    @Test
    public void videoHappyPath() {
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.WAITING, UploadTaskStates.COMPRESSING));
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.COMPRESSING, UploadTaskStates.COMPRESSING));
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.COMPRESSING, UploadTaskStates.UPLOADING));
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.UPLOADING, UploadTaskStates.UPLOADING));
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.UPLOADING, UploadTaskStates.READY));
    }

    @Test
    public void photoSkipsCompressingStage() {
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.WAITING, UploadTaskStates.UPLOADING));
    }

    @Test
    public void failureAndRetry() {
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.COMPRESSING, UploadTaskStates.FAILED));
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.UPLOADING, UploadTaskStates.FAILED));
        // 退避重试回到等待
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.UPLOADING, UploadTaskStates.WAITING));
        // 点按重试
        assertTrue(UploadTaskStates.canTransit(UploadTaskStates.FAILED, UploadTaskStates.WAITING));
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.FAILED, UploadTaskStates.UPLOADING));
    }

    @Test
    public void readyIsFinal_andNoSkipToReady() {
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.READY, UploadTaskStates.WAITING));
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.READY, UploadTaskStates.FAILED));
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.WAITING, UploadTaskStates.READY));
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.COMPRESSING, UploadTaskStates.READY));
        assertFalse(UploadTaskStates.canTransit(UploadTaskStates.UPLOADING, UploadTaskStates.COMPRESSING));
    }

    @Test
    public void activeAndItemStatus() {
        assertTrue(UploadTaskStates.isActive(UploadTaskStates.WAITING));
        assertTrue(UploadTaskStates.isActive(UploadTaskStates.COMPRESSING));
        assertTrue(UploadTaskStates.isActive(UploadTaskStates.UPLOADING));
        assertFalse(UploadTaskStates.isActive(UploadTaskStates.READY));
        assertFalse(UploadTaskStates.isActive(UploadTaskStates.FAILED));
        assertFalse(UploadTaskStates.isActive(null));
        assertEquals(UploadItem.Status.DONE, UploadTaskStates.toItemStatus(UploadTaskStates.READY));
        assertEquals(UploadItem.Status.COMPRESSING, UploadTaskStates.toItemStatus(UploadTaskStates.COMPRESSING));
        assertEquals(UploadItem.Status.FAILED, UploadTaskStates.toItemStatus(UploadTaskStates.FAILED));
        assertEquals(UploadItem.Status.WAITING, UploadTaskStates.toItemStatus(null));
    }

    @Test
    public void decide_retriesTransientErrorsThreeTimes() {
        assertEquals(UploadTaskStates.ErrorDecision.RETRY, UploadTaskStates.decide(ErrorCodes.NETWORK, 0));
        assertEquals(UploadTaskStates.ErrorDecision.RETRY, UploadTaskStates.decide(50001, 1));
        assertEquals(UploadTaskStates.ErrorDecision.RETRY, UploadTaskStates.decide(503, 2));
        assertEquals(UploadTaskStates.ErrorDecision.FAIL, UploadTaskStates.decide(ErrorCodes.NETWORK, 3));
    }

    @Test
    public void decide_businessErrorsFailImmediately() {
        assertEquals(UploadTaskStates.ErrorDecision.FAIL, UploadTaskStates.decide(ErrorCodes.FILE_INVALID, 0));
        assertEquals(UploadTaskStates.ErrorDecision.FAIL, UploadTaskStates.decide(ErrorCodes.VIDEO_TOO_LARGE, 0));
        assertEquals(UploadTaskStates.ErrorDecision.FAIL, UploadTaskStates.decide(40101, 0));
        assertEquals(UploadTaskStates.ErrorDecision.FAIL, UploadTaskStates.decide(404, 0));
    }

    @Test
    public void progressThrottle() {
        assertFalse(UploadTaskStates.shouldWriteProgress(10, 12));
        assertTrue(UploadTaskStates.shouldWriteProgress(10, 15));
        assertTrue(UploadTaskStates.shouldWriteProgress(97, 100));
        // 进入新阶段进度从 0 开始
        assertTrue(UploadTaskStates.shouldWriteProgress(100, 0));
    }

    @Test
    public void failureText() {
        assertEquals("视频超过 30 秒", UploadTaskStates.failureText(ErrorCodes.VIDEO_TOO_LARGE, "视频超过 30 秒"));
        assertEquals(UploadTaskStates.MSG_UPLOAD_FAILED, UploadTaskStates.failureText(ErrorCodes.NETWORK, "timeout"));
        assertEquals(UploadTaskStates.MSG_UPLOAD_FAILED, UploadTaskStates.failureText(ErrorCodes.FILE_INVALID, null));
    }
}
