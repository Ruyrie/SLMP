package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.enums.SmsScene;
import com.zhimu.server.biz.account.request.AccountSmsCodeRequest;
import com.zhimu.server.biz.account.request.SmsCodeSendRequest;
import com.zhimu.server.biz.account.vo.SmsCodeSendVO;

/** API-A01、A02 发送验证码；其他接口校验并消费验证码。 */
public interface SmsCodeService {

    SmsCodeSendVO send(SmsCodeSendRequest request);

    SmsCodeSendVO sendToSelf(AccountSmsCodeRequest request);

    /** 校验并消费（成功即删除） */
    void verifyAndConsume(SmsScene scene, String phone, String code, String field);

    /** 只校验不消费（A10 先校验新手机号验证码，创建申请成功后再消费） */
    void verify(SmsScene scene, String phone, String code, String field);
}
