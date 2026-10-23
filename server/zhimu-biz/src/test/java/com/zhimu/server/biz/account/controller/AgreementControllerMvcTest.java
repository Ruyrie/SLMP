package com.zhimu.server.biz.account.controller;

import com.zhimu.server.biz.account.service.AgreementService;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import com.zhimu.server.biz.account.vo.AgreementItemVO;
import com.zhimu.server.biz.account.vo.AgreementVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AgreementControllerMvcTest {

    @Test
    void A21无需登录返回协议与客服电话() throws Exception {
        AgreementService service = mock(AgreementService.class);
        when(service.current()).thenReturn(AgreementVO.builder()
                .userAgreement(AgreementItemVO.builder().version("2026-10-01").title("用户协议")
                        .url("https://www.zhimutong.example.com/agreements/user-2026-10-01.html").build())
                .privacyPolicy(AgreementItemVO.builder().version("2026-10-01").title("隐私政策")
                        .url("https://www.zhimutong.example.com/agreements/privacy-2026-10-01.html").build())
                .agreementVersion("2026-10-01").customerServicePhone("400-123-4567").build());
        MockMvc mvc = AccountTestMvc.builder(new AgreementController(service)).build();

        JsonNode data = AccountTestMvc.json(mvc.perform(get("/api/v1/agreements/current"))
                .andReturn().getResponse().getContentAsString()).get("data");

        assertThat(data.get("agreement_version").asString()).isEqualTo("2026-10-01");
        assertThat(data.get("customer_service_phone").asString()).isEqualTo("400-123-4567");
        assertThat(data.get("user_agreement").get("url").asString()).startsWith("https://");
        assertThat(data.get("privacy_policy").get("title").asString()).isEqualTo("隐私政策");
    }
}
