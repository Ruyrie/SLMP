package com.zhimu.server.biz.account.service;

import com.zhimu.server.biz.account.vo.AgreementVO;

/** API-A21 当前协议版本、地址与客服电话（配置驱动，无需登录）。 */
public interface AgreementService {

    AgreementVO current();
}
