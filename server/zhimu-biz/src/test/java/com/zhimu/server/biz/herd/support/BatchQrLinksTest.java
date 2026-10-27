package com.zhimu.server.biz.herd.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BatchQrLinksTest {
    private static final String TOKEN = "k7Rm2xQ9pT";

    @Test void generatesTheConfiguredLocalAndPublicAddress() {
        assertThat(new BatchQrLinks("http://127.0.0.1:5173/").content(TOKEN))
                .isEqualTo("http://127.0.0.1:5173/s/b/" + TOKEN);
        assertThat(new BatchQrLinks("http://8.208.12.217").content(TOKEN))
                .isEqualTo("http://8.208.12.217/s/b/" + TOKEN);
        assertThat(new BatchQrLinks("https://farm.example.test/user/").content(TOKEN))
                .isEqualTo("https://farm.example.test/user/s/b/" + TOKEN);
    }

    @Test void acceptsNewAndPreviouslyPrintedLabelsWithoutChangingTheToken() {
        BatchQrLinks links = new BatchQrLinks("http://8.208.12.217");
        for (String token : new String[]{TOKEN, "Xl8cQ2fN4w", "BRD000015A"}) {
            assertThat(links.parseToken(links.content(token))).isEqualTo(token);
            assertThat(links.parseToken("  " + QrCodes.content(token) + "  ")).isEqualTo(token);
        }
    }

    @Test void rejectsForeignSitesAndNonAlphanumericOrWrongLengthTokens() {
        BatchQrLinks links = new BatchQrLinks("http://8.208.12.217");
        for (String value : new String[]{"https://other.example.test/s/b/" + TOKEN,
                "http://8.208.12.217.evil.test/s/b/" + TOKEN,
                links.content(TOKEN) + "?other=1", links.content(TOKEN) + "/extra",
                links.content("k7Rm2xQ9p_"), links.content("k7Rm2xQ9p中"), links.content("short")}) {
            assertThat(links.parseToken(value)).as(value).isNull();
        }
        assertThat(links.parseToken(null)).isNull();
    }

    @Test void rejectsMisconfiguredBaseAddressesAtStartup() {
        for (String value : new String[]{"", "/user", "ftp://farm.example.test",
                "https://user:secret@farm.example.test", "https://farm.example.test?x=1",
                "https://farm.example.test/#part"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new BatchQrLinks(value));
        }
    }
}
