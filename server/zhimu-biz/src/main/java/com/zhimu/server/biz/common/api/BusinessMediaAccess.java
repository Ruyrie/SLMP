package com.zhimu.server.biz.common.api;

import com.zhimu.server.common.enums.BizType;
import com.zhimu.server.common.enums.FileScene;

/** 非养殖场媒体的业务授权扩展，避免把平台通知配图当成所有登录用户都可查看的文件。 */
public interface BusinessMediaAccess {
    BizType bizType();
    FileScene scene();
    boolean canViewRecord(Long recordId, Long userId);
    boolean canViewFile(Long fileId, Long userId);
}
