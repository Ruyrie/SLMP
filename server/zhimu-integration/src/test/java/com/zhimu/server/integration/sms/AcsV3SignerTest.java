package com.zhimu.server.integration.sms;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3 签名：测试向量取自 App 的 AcsV3SignerTest（阿里云《V3 版本请求体 &amp; 签名机制》文档示例，RunInstances，2023-10-26T10:22:32Z）。
 */
class AcsV3SignerTest {

    private static final String DOC_AUTHORIZATION = "ACS3-HMAC-SHA256 Credential=YourAccessKeyId,"
            + "SignedHeaders=host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version,"
            + "Signature=06563a9e1b43f5dfe96b81484da74bceab24a1d853912eee15083a6f0f3283c0";

    private static AcsV3Signer.Signed signDocExample() {
        Map<String, String> query = new LinkedHashMap<>();
        // 故意倒序放入，验证按名称排序
        query.put("RegionId", "cn-shanghai");
        query.put("ImageId", "win2019_1809_x64_dtc_zh-cn_40G_alibase_20230811.vhd");
        return AcsV3Signer.sign("YourAccessKeyId", "YourAccessKeySecret", "POST", "ecs.cn-shanghai.aliyuncs.com",
                "RunInstances", "2014-05-26", query, "2023-10-26T10:22:32Z", "3156853299f313e23d1673dc12e1703d");
    }

    @Test
    void docExample_canonicalRequest() {
        String expected = "POST\n"
                + "/\n"
                + "ImageId=win2019_1809_x64_dtc_zh-cn_40G_alibase_20230811.vhd&RegionId=cn-shanghai\n"
                + "host:ecs.cn-shanghai.aliyuncs.com\n"
                + "x-acs-action:RunInstances\n"
                + "x-acs-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"
                + "x-acs-date:2023-10-26T10:22:32Z\n"
                + "x-acs-signature-nonce:3156853299f313e23d1673dc12e1703d\n"
                + "x-acs-version:2014-05-26\n"
                + "\n"
                + "host;x-acs-action;x-acs-content-sha256;x-acs-date;x-acs-signature-nonce;x-acs-version\n"
                + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertThat(signDocExample().canonicalRequest()).isEqualTo(expected);
    }

    @Test
    void docExample_stringToSignAndSignature() {
        AcsV3Signer.Signed s = signDocExample();
        assertThat(s.stringToSign())
                .isEqualTo("ACS3-HMAC-SHA256\n7ea06492da5221eba5297e897ce16e55f964061054b7695beedaac1145b1e259");
        assertThat(s.signature()).isEqualTo("06563a9e1b43f5dfe96b81484da74bceab24a1d853912eee15083a6f0f3283c0");
        assertThat(s.authorization()).isEqualTo(DOC_AUTHORIZATION);
        assertThat(s.headers().get("authorization")).isEqualTo(DOC_AUTHORIZATION);
        assertThat(s.headers().get("x-acs-date")).isEqualTo("2023-10-26T10:22:32Z");
        assertThat(s.headers().get("x-acs-content-sha256")).isEqualTo(AcsV3Signer.EMPTY_BODY_SHA256);
    }

    @Test
    void emptyBodyHash() {
        assertThat(AcsV3Signer.EMPTY_BODY_SHA256)
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void percentEncode_rules() {
        assertThat(AcsV3Signer.percentEncode("AZaz09-_.~")).isEqualTo("AZaz09-_.~");
        assertThat(AcsV3Signer.percentEncode("a b*c+")).isEqualTo("a%20b%2Ac%2B");
        assertThat(AcsV3Signer.percentEncode("恒创联众")).isEqualTo("%E6%81%92%E5%88%9B%E8%81%94%E4%BC%97");
        assertThat(AcsV3Signer.percentEncode("{\"code\":\"##code##\",\"min\":\"5\"}"))
                .isEqualTo("%7B%22code%22%3A%22%23%23code%23%23%22%2C%22min%22%3A%225%22%7D");
    }

    @Test
    void canonicalQuery_sortedAndEncoded() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("TemplateCode", "100001");
        q.put("SignName", "恒创联众");
        q.put("PhoneNumber", "13800006620");
        q.put("Empty", "");
        assertThat(AcsV3Signer.canonicalQuery(q)).isEqualTo(
                "Empty=&PhoneNumber=13800006620&SignName=%E6%81%92%E5%88%9B%E8%81%94%E4%BC%97&TemplateCode=100001");
    }

    @Test
    void formatDate_utcAndNonce() {
        assertThat(AcsV3Signer.formatDate(1698315752000L)).isEqualTo("2023-10-26T10:22:32Z");
        assertThat(AcsV3Signer.newNonce()).matches("[0-9a-f]{32}");
    }
}
