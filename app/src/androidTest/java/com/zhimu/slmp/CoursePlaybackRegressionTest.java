package com.zhimu.slmp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zhimu.slmp.account.data.AccountApi;
import com.zhimu.slmp.account.model.request.DeviceInfo;
import com.zhimu.slmp.account.model.request.PasswordLoginRequest;
import com.zhimu.slmp.ai.ui.history.WatchHistoryActivity;
import com.zhimu.slmp.ai.ui.play.CoursePlayActivity;
import com.zhimu.slmp.common.di.CoreComponent;
import com.zhimu.slmp.common.model.vo.AuthTokenVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.farm.api.FarmApi;
import com.zhimu.slmp.network.RxResult;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;
import retrofit2.http.Url;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/** Real local media only: visible cover, actual Media3 playback and server-persisted progress. */
@RunWith(AndroidJUnit4.class)
public class CoursePlaybackRegressionTest {
    private static final String TITLE = "QA三端视频封面联动20261026";
    private static final long COURSE_ID = 6305;
    private Instrumentation instrumentation;
    private Context context;
    private CoreComponent core;
    private boolean previousWifiOnly;
    private boolean preferenceCaptured;

    @Before public void useRealLocalService() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        core = LocalQaEnvironment.requireLocalCore(context);
        AuthTokenVO token = core.retrofit().create(AccountApi.class)
                .loginByPassword(new PasswordLoginRequest("13800006620", "Zmt12345",
                        new DeviceInfo("pixel9-course-regression", "Pixel 9 Pro", "Android")))
                .compose(RxResult.<AuthTokenVO>unwrap()).blockingGet();
        core.sessionManager().saveToken(token);
        FarmContextVO farm = core.retrofit().create(FarmApi.class).switchFarm(1001L)
                .compose(RxResult.<FarmContextVO>unwrap()).blockingGet();
        core.farmContext().update(farm);
        previousWifiOnly = core.appSettings().isVideoWifiOnly();
        preferenceCaptured = true;
        core.appSettings().setVideoWifiOnly(false);
    }

    @After public void restorePreferences() {
        if (preferenceCaptured) core.appSettings().setVideoWifiOnly(previousWifiOnly);
    }

    public interface QaApi {
        @GET Single<ApiResponse<JsonObject>> get(@Url String path);
    }

    private JsonObject api(String path) {
        ApiResponse<JsonObject> response = core.retrofit().create(QaApi.class).get("api/v1" + path).blockingGet();
        assertTrue("Local QA API failed: " + path, response.isSuccess());
        return response.getData();
    }

    @Test public void classroomLoadsTheUploadedCoverPlaysRealHlsAndPersistsHistory() throws Exception {
        JsonObject card = null;
        for (JsonElement item : api("/courses?keyword=" + URLEncoder.encode(TITLE, "UTF-8") + "&page=1&size=20").getAsJsonArray("list")) {
            if (item.getAsJsonObject().get("id").getAsLong() == COURSE_ID) card = item.getAsJsonObject();
        }
        assertNotNull("Course 6305 must first be published through the local admin", card);
        String coverUrl = card.get("cover_url").getAsString();
        assertFalse("The real course must have a cover URL", coverUrl.isEmpty());
        Bitmap expectedCover;
        java.net.URLConnection coverConnection = new URL(coverUrl).openConnection();
        coverConnection.setConnectTimeout(15000);
        coverConnection.setReadTimeout(15000);
        try (InputStream input = coverConnection.getInputStream()) {
            expectedCover = BitmapFactory.decodeStream(input);
        }
        assertNotNull("The uploaded cover must actually be retrievable", expectedCover);
        int expectedColor = expectedCover.getPixel(expectedCover.getWidth() / 2, expectedCover.getHeight() / 2);
        expectedCover.recycle();
        String previousWatch = lastWatchedAt();

        Activity main = instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        onView(allOf(withText("助手"), isDisplayed(), isDescendantOfA(withId(R.id.main_bottom_nav)))).perform(click());
        onView(withId(com.zhimu.slmp.ai.R.id.ai_mode_course)).perform(click());
        onView(withContentDescription("搜索课程")).perform(click());
        onView(withId(com.zhimu.slmp.ai.R.id.ai_course_search_input)).perform(replaceText(TITLE), closeSoftKeyboard());

        ImageView cover = waitForCover(main, 25000);
        assertNotNull("A real bitmap must load beside the matching course title", cover);
        AtomicReference<Integer> actualColor = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            Bitmap bitmap = ((BitmapDrawable) cover.getDrawable()).getBitmap();
            actualColor.set(bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2));
        });
        assertTrue("Displayed cover must match the course's current bound image",
                Math.abs(Color.red(expectedColor) - Color.red(actualColor.get())) < 35
                && Math.abs(Color.green(expectedColor) - Color.green(actualColor.get())) < 35
                && Math.abs(Color.blue(expectedColor) - Color.blue(actualColor.get())) < 35);
        capture("course-list-real-cover");
        onView(allOf(withId(com.zhimu.slmp.ai.R.id.ai_card_title), withText(TITLE), isDisplayed())).perform(click());
        Activity play = waitFor(CoursePlayActivity.class, 20000);
        assertNotNull("The list must navigate to the actual course player", play);
        Playback ready = waitForPlayback(play, false, 30000);
        assertNotNull("Real HLS must reach Media3 READY/PLAYING", ready);
        Playback advanced = waitForPlayback(play, true, 15000);
        assertNotNull("Playback position must advance using actual media", advanced);
        assertTrue(advanced.position >= 1000);
        capture("course-real-hls-playing");
        pressBack();
        assertNotNull("Back must return to the classroom", waitFor(MainActivity.class, 10000));

        JsonObject persisted = null;
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            persisted = api("/courses/" + COURSE_ID);
            JsonObject progress = persisted.getAsJsonObject("progress");
            if (progress != null && progress.get("position_sec").getAsInt() >= 1) break;
            SystemClock.sleep(200);
        }
        assertNotNull(persisted);
        assertTrue("Leaving playback must persist the actual position", persisted.getAsJsonObject("progress").get("position_sec").getAsInt() >= 1);
        assertNotEquals("Playback must update real server history rather than reuse an older record", previousWatch, lastWatchedAt());
        onView(withId(com.zhimu.slmp.ai.R.id.ai_course_search_back)).perform(click());
        if (!persisted.getAsJsonObject("progress").get("completed").getAsBoolean()) {
            waitForText(main, com.zhimu.slmp.ai.R.id.ai_head_continue_title, TITLE, 20000);
            capture("classroom-refreshed-continue");
        }
        onView(withContentDescription("观看记录")).perform(click());
        Activity history = waitFor(WatchHistoryActivity.class, 15000);
        assertNotNull(history);
        waitForText(history, com.zhimu.slmp.ai.R.id.ai_tc_title, TITLE, 20000);
        capture("course-history-persisted");
    }

    private ImageView waitForCover(Activity activity, long timeout) {
        AtomicReference<ImageView> result = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                View title = findTitle(activity.getWindow().getDecorView());
                for (View parent = title == null ? null : (View) title.getParent(); parent != null;
                     parent = parent.getParent() instanceof View ? (View) parent.getParent() : null) {
                    ImageView image = parent.findViewById(com.zhimu.slmp.ai.R.id.ai_card_cover);
                    if (image != null && image.getDrawable() instanceof BitmapDrawable) {
                        result.set(image);
                        break;
                    }
                }
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(200);
        }
        return null;
    }

    private View findTitle(View root) {
        if (root.getId() == com.zhimu.slmp.ai.R.id.ai_card_title && root instanceof TextView
                && TITLE.contentEquals(((TextView) root).getText()) && root.isShown()) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findTitle(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Playback { int state; long position; boolean playing; String error; }

    private Playback waitForPlayback(Activity activity, boolean advanced, long timeout) throws Exception {
        Class<?> playerType = Class.forName("androidx.media3.common.Player");
        AtomicReference<Playback> latest = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                try {
                    View view = activity.findViewById(com.zhimu.slmp.ai.R.id.ai_play_player);
                    Object player = view.getClass().getMethod("getPlayer").invoke(view);
                    if (player == null) return;
                    Playback sample = new Playback();
                    sample.state = (Integer) playerType.getMethod("getPlaybackState").invoke(player);
                    sample.playing = (Boolean) playerType.getMethod("isPlaying").invoke(player);
                    sample.position = (Long) playerType.getMethod("getCurrentPosition").invoke(player);
                    Object error = playerType.getMethod("getPlayerError").invoke(player);
                    sample.error = error == null ? null : error.toString();
                    latest.set(sample);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            Playback sample = latest.get();
            if (sample != null && sample.error != null) fail("Real player error: " + sample.error);
            if (sample != null && (sample.state == 3 || sample.playing || advanced && sample.state == 4)
                    && (!advanced || sample.position >= 1000)) return sample;
            SystemClock.sleep(100);
        }
        return null;
    }

    private void waitForText(Activity activity, int id, String expected, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                found.set(hasText(activity.getWindow().getDecorView(), id, expected));
            });
            if (found.get()) return;
            SystemClock.sleep(200);
        }
        fail("Expected text not rendered: " + expected);
    }

    private boolean hasText(View root, int id, String expected) {
        if (root.getId() == id && root instanceof TextView && expected.contentEquals(((TextView) root).getText())) return true;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), id, expected)) return true;
        }
        return false;
    }

    private String lastWatchedAt() {
        for (JsonElement item : api("/course-progress?page=1&size=100").getAsJsonArray("list")) {
            JsonObject record = item.getAsJsonObject();
            if (record.get("id").getAsLong() == COURSE_ID) {
                return record.has("last_watched_at") && !record.get("last_watched_at").isJsonNull()
                        ? record.get("last_watched_at").getAsString() : null;
            }
        }
        return null;
    }

    private Activity waitFor(Class<? extends Activity> type, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        AtomicReference<Activity> result = new AtomicReference<>();
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity)) result.set(activity);
                }
            });
            if (result.get() != null) return result.get();
            SystemClock.sleep(200);
        }
        return null;
    }

    private void capture(String name) throws Exception {
        File folder = new File(context.getExternalFilesDir(null), "course-playback-regression");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { screenshot.recycle(); }
    }
}
