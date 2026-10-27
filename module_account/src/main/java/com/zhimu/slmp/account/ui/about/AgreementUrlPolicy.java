package com.zhimu.slmp.account.ui.about;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** HTTPS 协议页面，以及应用已配置的 HTTP 服务同源页面。 */
final class AgreementUrlPolicy {
    private AgreementUrlPolicy() { }

    static boolean isAllowed(String url, String baseUrl) {
        if (url == null || url.isBlank()) return false;
        try {
            URI target = new URI(url.trim());
            String host = target.getHost();
            if (host == null || target.getRawUserInfo() != null
                    || target.getPort() > 65535 || target.getPort() == 0) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String placeholder : new String[]{"example.com", "example.org", "example.net"}) {
                if (host.equals(placeholder) || host.endsWith("." + placeholder)) return false;
            }
            if ("https".equalsIgnoreCase(target.getScheme())) return true;
            if (!"http".equalsIgnoreCase(target.getScheme()) || baseUrl == null) return false;
            URI base = new URI(baseUrl);
            return "http".equalsIgnoreCase(base.getScheme())
                    && host.equalsIgnoreCase(base.getHost())
                    && httpPort(target) == httpPort(base);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static int httpPort(URI uri) {
        return uri.getPort() == -1 ? 80 : uri.getPort();
    }
}
