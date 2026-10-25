package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.mapper.NoticeRecipientMapper;
import com.zhimu.server.biz.common.api.BusinessMediaAccess;
import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 仅已送达且未撤回的通知接收人可以刷新配图地址。 */
@Service
@RequiredArgsConstructor
public class NoticeMediaAccess implements BusinessMediaAccess {
    private final NoticeRecipientMapper recipients;
    public BizType bizType() { return BizType.NOTICE; }
    public FileScene scene() { return FileScene.NOTICE_IMAGE; }
    public boolean canViewRecord(Long id, Long userId) {
        return userId != null && recipients.countReadableNotice(id, userId) > 0;
    }
    public boolean canViewFile(Long id, Long userId) {
        return userId != null && recipients.countReadableImage(id, userId) > 0;
    }
}
