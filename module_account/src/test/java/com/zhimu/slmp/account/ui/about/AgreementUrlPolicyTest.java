package com.zhimu.slmp.account.ui.about;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgreementUrlPolicyTest {
    private static final String BASE = "http://8.208.12.217/";

    @Test public void allowsConfiguredOriginAndHttpsPolicies() {
        assertTrue(AgreementUrlPolicy.isAllowed(BASE + "agreements/user-2026-10-26.html", BASE));
        assertTrue(AgreementUrlPolicy.isAllowed("http://8.208.12.217:80/agreements/privacy.html", BASE));
        assertTrue(AgreementUrlPolicy.isAllowed("https://lbs.amap.com/pages/privacy/", BASE));
    }

    @Test public void rejectsUntrustedCleartextAndNonWebUrls() {
        for (String url : new String[]{null, "", "javascript:alert(1)", "file:///sdcard/policy.html",
                "content://policy", "//8.208.12.217/privacy.html", "https:///privacy.html",
                "http://8.208.12.217.evil.test/privacy.html", "http://8.208.12.217:8080/privacy.html",
                "http://8.208.12.217@evil.test/privacy.html", "https://user:pass@lbs.amap.com/",
                "https://www.zhimutong.example.com/agreements/privacy.html"}) {
            assertFalse(String.valueOf(url), AgreementUrlPolicy.isAllowed(url, BASE));
        }
        assertFalse(AgreementUrlPolicy.isAllowed(BASE + "agreements/user.html", "https://8.208.12.217/"));
    }
}
