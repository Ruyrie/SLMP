package com.zhimu.slmp.media.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.zhimu.slmp.common.model.vo.FileVO;

import org.junit.Test;

public class UploadItemTest {

    private static FileVO file(long id) {
        return new Gson().fromJson("{\"fileId\":" + id + ",\"url\":\"https://files.example.com/a.jpg\","
                + "\"thumbUrl\":\"https://files.example.com/a_t.jpg\",\"mediaType\":\"IMAGE\"}", FileVO.class);
    }

    @Test
    public void newLocal_isWaiting() {
        UploadItem item = UploadItem.newLocal("content://media/1");
        assertEquals(UploadItem.Status.WAITING, item.getStatus());
        assertNotNull(item.getId());
        assertTrue(item.isInProgress());
        assertFalse(item.isRemote());
        assertEquals(0, item.getFileId());
    }

    @Test
    public void fullFlow_waitingToDone() {
        UploadItem waiting = UploadItem.newLocal("content://media/1");
        UploadItem compressing = waiting.toCompressing();
        UploadItem uploading = compressing.toUploading("/cache/compress/a.jpg");
        UploadItem half = uploading.withProgress(50);
        UploadItem done = half.toDone(file(9100));

        assertEquals(UploadItem.Status.COMPRESSING, compressing.getStatus());
        assertEquals(UploadItem.Status.UPLOADING, uploading.getStatus());
        assertEquals(0, uploading.getProgress());
        assertEquals("/cache/compress/a.jpg", uploading.getLocalPath());
        assertEquals(50, half.getProgress());
        assertEquals(UploadItem.Status.DONE, done.getStatus());
        assertEquals(9100, done.getFileId());
        assertEquals(100, done.getProgress());
        assertEquals("https://files.example.com/a_t.jpg", done.getThumbUrl());
        assertTrue(done.isDone());
        assertFalse(done.isInProgress());
        // 不可变：每一步返回新对象，id 不变
        assertEquals(UploadItem.Status.WAITING, waiting.getStatus());
        assertEquals(waiting.getId(), done.getId());
    }

    @Test
    public void progress_sameValueReturnsSelf_andIsClamped() {
        UploadItem uploading = UploadItem.newLocal("file:///a.jpg").toCompressing().toUploading("/a.jpg");
        UploadItem p30 = uploading.withProgress(30);
        assertSame(p30, p30.withProgress(30));
        assertEquals(100, uploading.withProgress(130).getProgress());
        assertEquals(0, p30.withProgress(-5).getProgress());
    }

    @Test
    public void progress_onlyWhileUploading() {
        UploadItem waiting = UploadItem.newLocal("file:///a.jpg");
        assertThrows(IllegalStateException.class, () -> waiting.withProgress(10));
    }

    @Test
    public void failed_retryKeepsCompressedFile() {
        UploadItem failed = UploadItem.newLocal("file:///a.jpg").toCompressing().toUploading("/c/a.jpg")
                .withProgress(40).toFailed("网络连接失败，请检查网络后重试");
        assertTrue(failed.isFailed());
        assertEquals("网络连接失败，请检查网络后重试", failed.getError());
        assertEquals(0, failed.getProgress());

        UploadItem retry = failed.toRetry();
        assertEquals(UploadItem.Status.WAITING, retry.getStatus());
        assertNull(retry.getError());
        assertTrue(retry.hasCompressedFile());
        // 已压缩过：等待中可以直接上传
        assertEquals(UploadItem.Status.UPLOADING, retry.toUploading("/c/a.jpg").getStatus());
    }

    @Test
    public void compressFailure_goesToFailed() {
        UploadItem failed = UploadItem.newLocal("content://media/2").toCompressing()
                .toFailed("只支持 JPG 或 PNG 图片，单张不超过 5MB");
        assertTrue(failed.isFailed());
        assertFalse(failed.hasCompressedFile());
    }

    @Test
    public void illegalTransitions_throw() {
        UploadItem done = UploadItem.newLocal("a").toCompressing().toUploading("/a").toDone(file(1));
        assertThrows(IllegalStateException.class, done::toRetry);
        assertThrows(IllegalStateException.class, () -> done.toFailed("x"));
        assertThrows(IllegalStateException.class, () -> done.toUploading("/a"));

        UploadItem waiting = UploadItem.newLocal("a");
        assertThrows(IllegalStateException.class, () -> waiting.toDone(file(1)));
        assertThrows(IllegalStateException.class, waiting::toRetry);

        UploadItem failed = waiting.toFailed("x");
        assertThrows(IllegalStateException.class, () -> failed.toUploading("/a"));
        assertThrows(IllegalStateException.class, failed::toCompressing);
    }

    @Test
    public void interrupted_backToWaiting() {
        UploadItem uploading = UploadItem.newLocal("a").toCompressing().toUploading("/a").withProgress(70);
        UploadItem restored = uploading.toInterrupted();
        assertEquals(UploadItem.Status.WAITING, restored.getStatus());
        assertEquals(0, restored.getProgress());
        assertEquals("/a", restored.getLocalPath());

        UploadItem compressing = UploadItem.newLocal("a").toCompressing();
        assertEquals(UploadItem.Status.WAITING, compressing.toInterrupted().getStatus());

        UploadItem done = uploading.toDone(file(5));
        assertSame(done, done.toInterrupted());
        UploadItem failed = UploadItem.newLocal("a").toFailed("x");
        assertSame(failed, failed.toInterrupted());
    }

    @Test
    public void remote_isDoneAndCannotTransit() {
        UploadItem remote = UploadItem.remote(file(9001));
        assertTrue(remote.isRemote());
        assertTrue(remote.isDone());
        assertEquals(9001, remote.getFileId());
        assertEquals("https://files.example.com/a.jpg", remote.getUrl());
        for (UploadItem.Status to : UploadItem.Status.values()) {
            assertFalse(UploadItem.canTransit(UploadItem.Status.DONE, to));
        }
        assertNotEquals(UploadItem.remote(file(9001)).getId(), remote.getId());
    }

    @Test
    public void tag_isKeptThroughTransitions() {
        UploadItem tagged = UploadItem.newLocal("a").withTag("新增", 1);
        UploadItem done = tagged.toCompressing().toUploading("/a").toDone(file(2));
        assertEquals("新增", done.getTagText());
        assertEquals(1, done.getTagType());
    }

    @Test
    public void canTransit_table() {
        assertTrue(UploadItem.canTransit(UploadItem.Status.WAITING, UploadItem.Status.COMPRESSING));
        assertTrue(UploadItem.canTransit(UploadItem.Status.COMPRESSING, UploadItem.Status.UPLOADING));
        assertTrue(UploadItem.canTransit(UploadItem.Status.UPLOADING, UploadItem.Status.DONE));
        assertTrue(UploadItem.canTransit(UploadItem.Status.UPLOADING, UploadItem.Status.FAILED));
        assertTrue(UploadItem.canTransit(UploadItem.Status.FAILED, UploadItem.Status.WAITING));
        assertFalse(UploadItem.canTransit(UploadItem.Status.FAILED, UploadItem.Status.DONE));
        assertFalse(UploadItem.canTransit(UploadItem.Status.WAITING, UploadItem.Status.DONE));
        assertFalse(UploadItem.canTransit(UploadItem.Status.COMPRESSING, UploadItem.Status.DONE));
    }
}
