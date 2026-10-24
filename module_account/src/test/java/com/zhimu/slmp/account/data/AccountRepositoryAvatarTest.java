package com.zhimu.slmp.account.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.account.model.request.AvatarBindRequest;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FileVO;
import com.zhimu.slmp.common.session.SessionManager;
import com.zhimu.slmp.network.GsonFactory;
import com.zhimu.slmp.network.model.ApiResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import io.reactivex.rxjava3.core.Single;
import okhttp3.MultipartBody;

/**
 * AccountRepository 头像链路：Uri → 临时文件 → POST files?scene=AVATAR → PUT account/avatar；
 * 兼容上传结果中的 file_id 与 id；结束删除临时文件；DELETE account/avatar。
 */
public class AccountRepositoryAvatarTest {

    private static final String AVATAR_URL = "https://cdn.example.com/avatar.png";

    @Rule
    public RxTestRule rx = new RxTestRule();
    @Rule
    public TemporaryFolder cacheDir = new TemporaryFolder();

    private AccountApi api;
    private AccountApi uploadApi;
    private ContentResolver resolver;
    private Uri uri;
    private AccountRepository repository;

    @Before
    public void setUp() throws Exception {
        api = mock(AccountApi.class);
        uploadApi = mock(AccountApi.class);
        resolver = mock(ContentResolver.class);
        Context context = mock(Context.class);
        when(context.getCacheDir()).thenReturn(cacheDir.getRoot());
        when(context.getContentResolver()).thenReturn(resolver);
        repository = new AccountRepository(api, uploadApi, mock(SessionManager.class), mock(FarmContext.class),
                mock(DirectSmsGateway.class), context);

        uri = mock(Uri.class);
        when(resolver.getType(uri)).thenReturn("image/png");
        when(resolver.openInputStream(uri)).thenReturn(
                new ByteArrayInputStream("fake-png-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void uploadAvatar_uploadsWithSceneThenBindsFileId() {
        FileVO file = TestData.fromJson("{\"file_id\":123,\"url\":\"" + AVATAR_URL + "\"}", FileVO.class);
        AccountUserVO user = TestData.fromJson("{\"user_id\":10001,\"name\":\"王建国\",\"name_initial\":\"王\","
                + "\"avatar_url\":\"" + AVATAR_URL + "\"}", AccountUserVO.class);
        when(uploadApi.uploadAvatar(any(), eq(AccountRepository.SCENE_AVATAR)))
                .thenReturn(Single.just(new ApiResponse<>(0, "ok", file, null)));
        when(api.bindAvatar(any())).thenReturn(Single.just(new ApiResponse<>(0, "ok", user, null)));

        AccountUserVO result = repository.uploadAvatar(uri).blockingGet();

        ArgumentCaptor<MultipartBody.Part> partCaptor = ArgumentCaptor.forClass(MultipartBody.Part.class);
        verify(uploadApi).uploadAvatar(partCaptor.capture(), eq("AVATAR"));
        verify(api, never()).uploadAvatar(any(), any());
        MultipartBody.Part part = partCaptor.getValue();
        assertEquals("image/png", part.body().contentType().toString());
        String disposition = String.valueOf(part.headers().get("Content-Disposition"));
        assertTrue(disposition.contains("name=\"file\""));
        assertTrue(disposition.contains(".png"));

        ArgumentCaptor<AvatarBindRequest> bodyCaptor = ArgumentCaptor.forClass(AvatarBindRequest.class);
        verify(api).bindAvatar(bodyCaptor.capture());
        assertEquals(123L, bodyCaptor.getValue().getFileId());
        assertEquals(AVATAR_URL, result.getAvatarUrl());
        assertCacheEmpty();
    }

    @Test
    public void uploadAvatar_acceptsIdAliasInUploadResult() {
        FileVO file = TestData.fromJson("{\"id\":456,\"url\":\"" + AVATAR_URL + "\"}", FileVO.class);
        AccountUserVO user = TestData.fromJson("{\"user_id\":10001,\"avatar_url\":\"" + AVATAR_URL + "\"}",
                AccountUserVO.class);
        when(uploadApi.uploadAvatar(any(), eq(AccountRepository.SCENE_AVATAR)))
                .thenReturn(Single.just(new ApiResponse<>(0, "ok", file, null)));
        when(api.bindAvatar(any())).thenReturn(Single.just(new ApiResponse<>(0, "ok", user, null)));

        repository.uploadAvatar(uri).blockingGet();

        ArgumentCaptor<AvatarBindRequest> bodyCaptor = ArgumentCaptor.forClass(AvatarBindRequest.class);
        verify(api).bindAvatar(bodyCaptor.capture());
        assertEquals(456L, bodyCaptor.getValue().getFileId());
    }

    @Test
    public void uploadAvatar_missingFileId_errorsWithoutBinding() {
        FileVO file = TestData.fromJson("{\"url\":\"" + AVATAR_URL + "\"}", FileVO.class);
        when(uploadApi.uploadAvatar(any(), eq(AccountRepository.SCENE_AVATAR)))
                .thenReturn(Single.just(new ApiResponse<>(0, "ok", file, null)));

        repository.uploadAvatar(uri).test().assertError(ApiException.class);

        verify(api, never()).bindAvatar(any());
        assertCacheEmpty();
    }

    @Test
    public void uploadAvatar_networkFailure_cleansTempFileAndDoesNotBind() {
        when(uploadApi.uploadAvatar(any(), eq(AccountRepository.SCENE_AVATAR)))
                .thenReturn(Single.error(new ApiException(ErrorCodes.FILE_INVALID, "图片格式不支持")));

        repository.uploadAvatar(uri).test().assertError(ApiException.class);

        verify(api, never()).bindAvatar(any());
        assertCacheEmpty();
    }

    @Test
    public void clearAvatar_returnsUserWithoutAvatar() {
        AccountUserVO user = TestData.fromJson("{\"user_id\":10001,\"name\":\"王建国\"}", AccountUserVO.class);
        when(api.clearAvatar()).thenReturn(Single.just(new ApiResponse<>(0, "ok", user, null)));

        AccountUserVO result = repository.clearAvatar().blockingGet();

        verify(api).clearAvatar();
        assertNull(result.getAvatarUrl());
    }

    @Test
    public void accountUser_avatarUrlMapped_andKeptWhenRenaming() {
        AccountUserVO user = TestData.fromJson("{\"user_id\":10001,\"name\":\"王建国\",\"avatar_url\":\""
                + AVATAR_URL + "\"}", AccountUserVO.class);

        assertEquals(AVATAR_URL, user.getAvatarUrl());
        assertEquals(AVATAR_URL, user.withName("李四", "李").getAvatarUrl());
        assertEquals("https://cdn.example.com/next.png",
                user.withAvatarUrl("https://cdn.example.com/next.png").getAvatarUrl());
        assertNull(user.withAvatarUrl(null).getAvatarUrl());
        assertTrue(GsonFactory.get().toJson(user).contains("\"avatar_url\":\"" + AVATAR_URL + "\""));
    }

    @Test
    public void avatarBindRequest_serializesFileIdAsSnakeCase() {
        assertEquals("{\"file_id\":123}", GsonFactory.get().toJson(new AvatarBindRequest(123)));
    }

    private void assertCacheEmpty() {
        File[] files = cacheDir.getRoot().listFiles();
        assertTrue(files == null || files.length == 0);
    }
}
