package com.zhimu.server.biz.account.service.impl;

import com.zhimu.server.biz.account.config.AccountProperties;
import com.zhimu.server.biz.account.service.AgreementService;
import com.zhimu.server.biz.account.vo.AgreementItemVO;
import com.zhimu.server.biz.account.vo.AgreementVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** API-A21：版本默认 2026-10-01，地址用 https 可配置占位，客服电话 400-123-4567。 */
@Service
@RequiredArgsConstructor
public class AgreementServiceImpl implements AgreementService {

    private final AccountProperties properties;

    @Override
    public AgreementVO current() {
        String version = properties.getAgreementVersion();
        String base = trimSlash(properties.getAgreementBaseUrl());
        return AgreementVO.builder()
                .userAgreement(AgreementItemVO.builder()
                        .version(version)
                        .title("用户协议")
                        .url(base + "/user-" + version + ".html")
                        .build())
                .privacyPolicy(AgreementItemVO.builder()
                        .version(version)
                        .title("隐私政策")
                        .url(base + "/privacy-" + version + ".html")
                        .build())
                .agreementVersion(version)
                .customerServicePhone(properties.getCustomerServicePhone())
                .build();
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://www.zhimutong.example.com/agreements";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
