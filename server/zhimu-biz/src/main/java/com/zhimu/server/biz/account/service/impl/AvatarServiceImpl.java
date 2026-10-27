package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.mapper.UserMapper;
import com.zhimu.server.biz.account.service.AvatarService;
import com.zhimu.server.biz.account.vo.AccountUserVO;
import com.zhimu.server.biz.common.entity.FileEntity;
import com.zhimu.server.biz.common.mapper.FileMapper;
import com.zhimu.server.biz.common.service.impl.FileVoAssembler;
import com.zhimu.server.biz.common.support.CommonErrors;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.FileKind;
import com.zhimu.server.common.enums.FileScene;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 头像绑定/清除：只接受本人上传、scene = AVATAR 的图片文件。
 * 地址由 {@link FileVoAssembler#url(FileEntity)} 每次签发，与 FileVO.url 使用同一套临时签名逻辑。
 */
@Service
@RequiredArgsConstructor
public class AvatarServiceImpl implements AvatarService {

    private final UserMapper userMapper;
    private final FileMapper fileMapper;
    private final FileVoAssembler fileVoAssembler;

    @Override
    @Transactional
    public AccountUserVO updateAvatar(Long fileId) {
        Long userId = UserContext.requireUserId();
        if (fileId == null) {
            throw CommonErrors.fieldError("file_id", "请选择头像文件");
        }
        User user = requireUser(userId);
        FileEntity file = fileMapper.selectActiveForUpdate(fileId);
        if (file == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!userId.equals(file.getCreatedBy())) {
            throw new BizException(ErrorCode.ROLE_FORBIDDEN, "只能使用自己上传的图片作为头像");
        }
        if (!FileScene.AVATAR.name().equals(file.getScene())) {
            throw CommonErrors.fieldError("file_id", "请使用头像上传场景上传图片");
        }
        if (!FileKind.IMAGE.name().equals(file.getFileKind())) {
            throw CommonErrors.fieldError("file_id", "头像必须是 JPG 或 PNG 图片");
        }
        int updated = userMapper.updateAvatar(userId, fileId, BeijingTime.now());
        if (updated == 0) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        user.setAvatarFileId(fileId);
        user.setProfileVersion((user.getProfileVersion() == null ? 0 : user.getProfileVersion()) + 1);
        return accountUser(user, fileVoAssembler.url(file));
    }

    @Override
    @Transactional
    public AccountUserVO clearAvatar() {
        Long userId = UserContext.requireUserId();
        User user = requireUser(userId);
        int updated = userMapper.clearAvatar(userId, BeijingTime.now());
        if (updated == 0) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        user.setAvatarFileId(null);
        return accountUser(user, null);
    }

    @Override
    public String avatarUrl(Long avatarFileId) {
        if (avatarFileId == null) {
            return null;
        }
        FileEntity file = fileMapper.selectById(avatarFileId);
        return file == null ? null : fileVoAssembler.url(file);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason", "EXPIRED");
        }
        if (!"NORMAL".equals(user.getStatus())) {
            throw new BizException(ErrorCode.SESSION_INVALID).with("reason",
                    "CANCELLED".equals(user.getStatus()) ? "ACCOUNT_CANCELLED" : "EXPIRED");
        }
        return user;
    }

    private static AccountUserVO accountUser(User user, String avatarUrl) {
        AccountUserVO vo = UserConvert.toAccountUser(user);
        if (vo != null) {
            vo.setAvatarUrl(avatarUrl);
        }
        return vo;
    }
}
