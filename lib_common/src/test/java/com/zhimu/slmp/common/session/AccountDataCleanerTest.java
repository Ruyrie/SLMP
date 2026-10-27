package com.zhimu.slmp.common.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class AccountDataCleanerTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void clearsOnlyAppPersonalFilesAndLeavesEvidenceAndUnownedCache() throws Exception {
        File cache = temp.newFolder("cache");
        File external = temp.newFolder("external-files");
        File report = write(new File(external, "Download/private-report.csv"));
        File evidence = write(new File(external, "account-membership/qa.png"));
        File unrelated = write(new File(cache, "unowned-sdk/config"));
        File voice = write(new File(cache, "voice/dictation.m4a"));
        File capture = write(new File(cache, "capture/photo.jpg"));
        File upload = write(new File(cache, "upload/task_7.mp4"));
        File avatar = write(new File(cache, "avatar_123.jpg"));
        AccountDataCleaner.clearPrivateFiles(cache, report.getParentFile());
        assertFalse(report.exists());
        assertFalse(voice.exists());
        assertFalse(capture.exists());
        assertFalse(upload.exists());
        assertFalse(avatar.exists());
        assertTrue(evidence.isFile());
        assertTrue(unrelated.isFile());
    }

    private static File write(File file) throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), "private qa data".getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
