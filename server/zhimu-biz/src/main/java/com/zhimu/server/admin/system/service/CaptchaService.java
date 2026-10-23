package com.zhimu.server.admin.system.service;

import com.zhimu.server.admin.system.vo.CaptchaVO;

/** 管理端图形验证码（ADM-S01）：4 位、2 分钟、一次有效、不区分大小写。 */
public interface CaptchaService {

    /** 生成验证码并写入 Redis admin:captcha:{captcha_id}，返回 PNG data URL */
    CaptchaVO create();

    /** 校验并删除验证码；不存在、已使用或不匹配都返回 false（调用方返回 40103） */
    boolean verify(String captchaId, String captchaCode);
}
