package com.zhimu.slmp.media.ui.camera;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.zhimu.slmp.media.camera.CameraResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class CameraCompletionTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    private CameraViewModel camera(Map<String, Object> values) {
        Map<String, Object> args = new HashMap<>(values);
        args.put("max_count", 6);
        CameraViewModel vm = new CameraViewModel();
        vm.attachSavedState(new SavedStateHandle(args));
        return vm;
    }

    @Test public void emptyCaptureCannotConfirm() {
        CameraViewModel vm = camera(Map.of());
        assertFalse(vm.getDoneEnabled().getValue());
        vm.onDone();
        assertNull(vm.getFinishEvent().getValue());
    }

    @Test public void confirmsNewPhotoPathsBackToTheForm() {
        CameraViewModel vm = camera(Map.of());
        vm.onPhotoSaved("/capture/photo-1.jpg");
        vm.onPhotoSaved("/capture/photo-2.jpg");
        assertTrue(vm.getDoneEnabled().getValue());
        assertEquals("完成（2）", vm.getDoneText().getValue());
        vm.onDone();
        CameraResult result = vm.getFinishEvent().getValue();
        assertEquals(CameraResult.TYPE_PHOTOS, result.getType());
        assertEquals(Arrays.asList("/capture/photo-1.jpg", "/capture/photo-2.jpg"), result.getPhotoPaths());
    }

    @Test public void captureInProgressCannotFinishAndLoseTheNewPhoto() {
        CameraViewModel vm = camera(Map.of());
        vm.onPermission(true);
        vm.onPhotoSaved("/capture/photo-1.jpg");
        vm.onShutter();
        assertFalse(vm.getDoneEnabled().getValue());
        vm.onDone();
        assertNull(vm.getFinishEvent().getValue());
        vm.onPhotoFailed();
        assertTrue(vm.getDoneEnabled().getValue());
    }

    @Test public void restoredPhotosStillHaveAConfirmationAction() {
        CameraViewModel vm = camera(Map.of("media_camera_captured",
                new ArrayList<>(Arrays.asList("/capture/restored.jpg"))));
        assertTrue(vm.getDoneEnabled().getValue());
        vm.onDone();
        assertEquals("/capture/restored.jpg", vm.getFinishEvent().getValue().getPhotoPaths().get(0));
    }

    @Test public void queuedCaptureCannotFinishWhileRecording() {
        CameraViewModel vm = camera(Map.of("allow_video", true, "page_code", "QA_REPORT",
                "media_camera_queued", 1));
        assertTrue(vm.getDoneEnabled().getValue());
        vm.onRecordStarted();
        assertFalse(vm.getDoneEnabled().getValue());
        vm.onDone();
        assertNull(vm.getFinishEvent().getValue());
        vm.onRecordFinalized(null, 0, false, "录像失败");
        assertTrue(vm.getDoneEnabled().getValue());
        vm.onDone();
        assertEquals(CameraResult.TYPE_QUEUED, vm.getFinishEvent().getValue().getType());
        assertEquals(1, vm.getFinishEvent().getValue().getQueuedCount());
    }

    @Test public void existingFormPhotosDoNotCountAsNewCaptures() {
        CameraViewModel vm = camera(Map.of("added_count", 3, "total_count", 6));
        assertFalse(vm.getDoneEnabled().getValue());
        assertEquals("完成", vm.getDoneText().getValue());
    }
}
