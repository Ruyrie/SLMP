package com.zhimu.slmp.account.ui.mine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.zhimu.slmp.account.RxTestRule;
import com.zhimu.slmp.account.TestData;
import com.zhimu.slmp.account.data.AccountRepository;
import com.zhimu.slmp.account.model.vo.AccountOverviewVO;
import com.zhimu.slmp.base.error.ApiException;
import com.zhimu.slmp.base.error.ErrorCodes;
import com.zhimu.slmp.common.farm.FarmContext;
import com.zhimu.slmp.common.model.vo.AccountUserVO;
import com.zhimu.slmp.common.model.vo.FarmContextVO;
import com.zhimu.slmp.common.session.SessionManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;

/**
 * A07 头像状态：上传中标志、成功后 overview / 会话立即更新、失败固定文案、恢复默认头像。
 */
public class MineViewModelAvatarTest {

    private static final String AVATAR_URL = "https://cdn.example.com/avatar.png";
    private static final String USER_JSON = "{\"user_id\":10001,\"name\":\"王建国\",\"name_initial\":\"王\","
            + "\"phone_masked\":\"138****6620\"}";
    private static final String USER_WITH_AVATAR_JSON = "{\"user_id\":10001,\"name\":\"王建国\",\"name_initial\":\"王\","
            + "\"phone_masked\":\"138****6620\",\"avatar_url\":\"" + AVATAR_URL + "\"}";
    private static final String OVERVIEW_JSON = "{\"user\":" + USER_JSON + ",\"profile_version\":3}";

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Rule
    public RxTestRule rx = new RxTestRule();

    private AccountRepository repository;
    private SessionManager sessionManager;
    private MutableLiveData<AccountUserVO> sessionUser;
    private MineViewModel vm;

    @Before
    public void setUp() {
        repository = mock(AccountRepository.class);
        sessionManager = mock(SessionManager.class);
        FarmContext farmContext = mock(FarmContext.class);
        sessionUser = new MutableLiveData<>(TestData.fromJson(USER_JSON, AccountUserVO.class));
        when(sessionManager.getUser()).thenReturn(sessionUser);
        when(sessionManager.currentUser()).thenAnswer(invocation -> sessionUser.getValue());
        when(farmContext.getContext()).thenReturn(new MutableLiveData<FarmContextVO>());
        when(repository.getOverview()).thenReturn(
                Single.just(TestData.fromJson(OVERVIEW_JSON, AccountOverviewVO.class)));

        vm = new MineViewModel(repository, sessionManager, farmContext);
        // Transformations.map 需要活跃观察者才会计算
        vm.getAvatarUrl().observeForever(url -> {
        });
        vm.getHasAvatar().observeForever(has -> {
        });
    }

    @Test
    public void uploadAvatar_success_updatesOverviewAndSessionImmediately() {
        Uri uri = mock(Uri.class);
        AccountUserVO updated = TestData.fromJson(USER_WITH_AVATAR_JSON, AccountUserVO.class);
        when(repository.uploadAvatar(uri)).thenReturn(Single.just(updated));

        vm.uploadAvatar(uri);

        assertFalse(Boolean.TRUE.equals(vm.getUploadingAvatar().getValue()));
        assertEquals(AVATAR_URL, vm.getAvatarUrl().getValue());
        assertEquals(Boolean.TRUE, vm.getHasAvatar().getValue());
        assertEquals(AVATAR_URL, vm.getOverview().getValue().getUser().getAvatarUrl());
        assertEquals(MineViewModel.MSG_AVATAR_UPDATED, vm.getMessage().getValue().getText());
        verify(sessionManager).updateUser(argThat(user -> AVATAR_URL.equals(user.getAvatarUrl())));
    }

    @Test
    public void uploadAvatar_failure_keepsInitialAndShowsFixedMessage() {
        Uri uri = mock(Uri.class);
        when(repository.uploadAvatar(uri)).thenReturn(
                Single.error(new ApiException(ErrorCodes.FILE_INVALID, "图片格式不支持")));

        vm.uploadAvatar(uri);

        assertFalse(Boolean.TRUE.equals(vm.getUploadingAvatar().getValue()));
        assertNull(vm.getAvatarUrl().getValue());
        assertEquals(MineViewModel.MSG_AVATAR_UPLOAD_FAILED, vm.getMessage().getValue().getText());
        verify(sessionManager, never()).updateUser(any());
    }

    @Test
    public void uploadAvatar_whileUploading_ignoresSecondSelection() {
        Uri uri = mock(Uri.class);
        SingleSubject<AccountUserVO> pending = SingleSubject.create();
        when(repository.uploadAvatar(uri)).thenReturn(pending);

        vm.uploadAvatar(uri);
        vm.uploadAvatar(uri);

        assertTrue(Boolean.TRUE.equals(vm.getUploadingAvatar().getValue()));
        verify(repository, times(1)).uploadAvatar(uri);

        pending.onSuccess(TestData.fromJson(USER_WITH_AVATAR_JSON, AccountUserVO.class));
        assertEquals(AVATAR_URL, vm.getAvatarUrl().getValue());
    }

    @Test
    public void clearAvatar_success_removesAvatarAndShowsMessage() {
        // 先显示头像，再恢复默认
        Uri uri = mock(Uri.class);
        when(repository.uploadAvatar(uri)).thenReturn(
                Single.just(TestData.fromJson(USER_WITH_AVATAR_JSON, AccountUserVO.class)));
        vm.uploadAvatar(uri);
        assertEquals(AVATAR_URL, vm.getAvatarUrl().getValue());

        AccountUserVO cleared = TestData.fromJson(USER_JSON, AccountUserVO.class);
        when(repository.clearAvatar()).thenReturn(Single.just(cleared));

        vm.clearAvatar();

        assertFalse(Boolean.TRUE.equals(vm.getUploadingAvatar().getValue()));
        assertNull(vm.getAvatarUrl().getValue());
        assertEquals(Boolean.FALSE, vm.getHasAvatar().getValue());
        assertEquals(MineViewModel.MSG_AVATAR_REMOVED, vm.getMessage().getValue().getText());
        assertNull(vm.getOverview().getValue().getUser().getAvatarUrl());
    }

    @Test
    public void clearAvatar_failure_keepsAvatarAndShowsFixedMessage() {
        Uri uri = mock(Uri.class);
        when(repository.uploadAvatar(uri)).thenReturn(
                Single.just(TestData.fromJson(USER_WITH_AVATAR_JSON, AccountUserVO.class)));
        vm.uploadAvatar(uri);

        when(repository.clearAvatar()).thenReturn(
                Single.error(new ApiException(ErrorCodes.NETWORK, "网络连接失败")));

        vm.clearAvatar();

        assertEquals(AVATAR_URL, vm.getAvatarUrl().getValue());
        assertEquals(MineViewModel.MSG_AVATAR_CLEAR_FAILED, vm.getMessage().getValue().getText());
    }
}
