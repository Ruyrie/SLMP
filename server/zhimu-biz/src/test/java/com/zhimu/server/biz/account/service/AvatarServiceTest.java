package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.service.impl.AvatarServiceImpl;
import com.zhimu.server.biz.account.test.FixedTime;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.mapper.FileVariantMapper;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.integration.storage.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 头像绑定/清除：文件存在、本人上传、scene = AVATAR、图片类型，以及返回的临时地址。
 */
class AvatarServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final FileMapper fileMapper = mock(FileMapper.class);
    private final FileVariantMapper variantMapper = mock(FileVariantMapper.class);
    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private AvatarServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedTime.set();
        FileVoAssembler assembler = new FileVoAssembler(fileMapper, variantMapper, objectStorage);
        service = new AvatarServiceImpl(userMapper, fileMapper, assembler);
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        when(objectStorage.presignedGetUrl(anyString(), anyString())).thenAnswer(invocation ->
                "https://files.test/" + invocation.getArgument(1));
    }

    @AfterEach
    void tearDown() {
        FixedTime.reset();
        UserContext.clear();
    }

    @Test
    void 绑定头像写入文件并返回临时地址() {
        User user = user();
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(fileMapper.selectById(900L)).thenReturn(file(900L, 10001L, "AVATAR", "IMAGE"));
        when(userMapper.updateAvatar(eq(10001L), eq(900L), any())).thenReturn(1);

        AccountUserVO vo = service.updateAvatar(900L);

        assertThat(vo.getUserId()).isEqualTo(10001L);
        assertThat(vo.getName()).isEqualTo("王建国");
        assertThat(vo.getAvatarUrl()).isEqualTo("https://files.test/avatar/20261023/900.jpg");
        assertThat(user.getAvatarFileId()).isEqualTo(900L);
        assertThat(user.getProfileVersion()).isEqualTo(4);
        verify(userMapper).updateAvatar(eq(10001L), eq(900L), any());
    }

    @Test
    void 清除头像置空并返回空地址() {
        User user = user();
        user.setAvatarFileId(900L);
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(userMapper.clearAvatar(eq(10001L), any())).thenReturn(1);

        AccountUserVO vo = service.clearAvatar();

        assertThat(vo.getAvatarUrl()).isNull();
        assertThat(user.getAvatarFileId()).isNull();
        verify(userMapper).clearAvatar(eq(10001L), any());
    }

    @Test
    void 非本人文件返回40303() {
        when(fileMapper.selectById(900L)).thenReturn(file(900L, 10002L, "AVATAR", "IMAGE"));

        BizException e = (BizException) catchException(() -> service.updateAvatar(900L));

        assertThat(e.getCode()).isEqualTo(40303);
        verify(userMapper, never()).updateAvatar(any(), any(), any());
    }

    @Test
    void 非头像场景返回40001() {
        when(fileMapper.selectById(900L)).thenReturn(file(900L, 10001L, "INSPECTION", "IMAGE"));

        BizException e = (BizException) catchException(() -> service.updateAvatar(900L));

        assertThat(e.getCode()).isEqualTo(40001);
        verify(userMapper, never()).updateAvatar(any(), any(), any());
    }

    @Test
    void 非图片返回40001() {
        when(fileMapper.selectById(900L)).thenReturn(file(900L, 10001L, "AVATAR", "VIDEO"));

        BizException e = (BizException) catchException(() -> service.updateAvatar(900L));

        assertThat(e.getCode()).isEqualTo(40001);
        assertThat(e.getMessage()).isEqualTo("头像必须是 JPG 或 PNG 图片");
    }

    @Test
    void 文件不存在返回40401() {
        when(fileMapper.selectById(999L)).thenReturn(null);

        BizException e = (BizException) catchException(() -> service.updateAvatar(999L));

        assertThat(e.getCode()).isEqualTo(40401);
        verify(userMapper, never()).updateAvatar(any(), any(), any());
    }

    @Test
    void 缺少文件ID返回40001() {
        BizException e = (BizException) catchException(() -> service.updateAvatar(null));

        assertThat(e.getCode()).isEqualTo(40001);
        verify(fileMapper, never()).selectById(any());
    }

    @Test
    void 头像地址查询文件不存在时返回空() {
        when(fileMapper.selectById(999L)).thenReturn(null);

        assertThat(service.avatarUrl(999L)).isNull();
        assertThat(service.avatarUrl(null)).isNull();
    }

    private static User user() {
        User user = new User();
        user.setId(10001L);
        user.setPhone("13800006620");
        user.setName("王建国");
        user.setStatus("NORMAL");
        user.setProfileVersion(3);
        return user;
    }

    private static FileEntity file(Long id, Long createdBy, String scene, String kind) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setBucket("zmt-private");
        file.setObjectKey("avatar/20261023/" + id + ".jpg");
        file.setScene(scene);
        file.setFileKind(kind);
        file.setUploadStatus("READY");
        file.setTranscodeStatus("NONE");
        file.setCreatedBy(createdBy);
        return file;
    }

    private static Throwable catchException(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable e) {
            return e;
        }
        throw new AssertionError("expected exception");
    }
}
