package com.zhimu.slmp.common.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V3 签名：用阿里云《V3 版本请求体 &amp; 签名机制》文档中的固定示例校验（RunInstances，2023-10-26T10:22:32Z）。
 */
public class AcsV3SignerTest {

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
    public void docExample_canonicalRequest() {
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
        assertEquals(expected, signDocExample().canonicalRequest);
    }

    @Test
    public void docExample_stringToSignAndSignature() {
        AcsV3Signer.Signed s = signDocExample();
        assertEquals("ACS3-HMAC-SHA256\n7ea06492da5221eba5297e897ce16e55f964061054b7695beedaac1145b1e259",
                s.stringToSign);
        assertEquals("06563a9e1b43f5dfe96b81484da74bceab24a1d853912eee15083a6f0f3283c0", s.signature);
        assertEquals(DOC_AUTHORIZATION, s.authorization);
        assertEquals(DOC_AUTHORIZATION, s.headers.get("authorization"));
        assertEquals("2023-10-26T10:22:32Z", s.headers.get("x-acs-date"));
        assertEquals(AcsV3Signer.EMPTY_BODY_SHA256, s.headers.get("x-acs-content-sha256"));
    }

    @Test
    public void emptyBodyHash() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AcsV3Signer.EMPTY_BODY_SHA256);
    }

    @Test
    public void percentEncode_rules() {
        assertEquals("AZaz09-_.~", AcsV3Signer.percentEncode("AZaz09-_.~"));
        assertEquals("a%20b%2Ac%2B", AcsV3Signer.percentEncode("a b*c+"));
        // 中文签名按 UTF-8 编码、大写十六进制
        assertEquals("%E6%81%92%E5%88%9B%E8%81%94%E4%BC%97", AcsV3Signer.percentEncode("恒创联众"));
        assertEquals("%7B%22code%22%3A%22%23%23code%23%23%22%2C%22min%22%3A%225%22%7D",
                AcsV3Signer.percentEncode(AliyunSmsClient.TEMPLATE_PARAM));
    }

    @Test
    public void canonicalQuery_sortedAndEncoded() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("TemplateCode", "100001");
        q.put("SignName", "恒创联众");
        q.put("PhoneNumber", "13800006620");
        q.put("Empty", "");
        assertEquals("Empty=&PhoneNumber=13800006620&SignName=%E6%81%92%E5%88%9B%E8%81%94%E4%BC%97&TemplateCode=100001",
                AcsV3Signer.canonicalQuery(q));
    }

    @Test
    public void formatDate_utc() {
        // 2023-10-26T10:22:32Z
        assertEquals("2023-10-26T10:22:32Z", AcsV3Signer.formatDate(1698315752000L));
    }

    @Test
    public void newNonce_32Hex() {
        String n = AcsV3Signer.newNonce();
        assertEquals(32, n.length());
        assertTrue(n.matches("[0-9a-f]{32}"));
    }
}
