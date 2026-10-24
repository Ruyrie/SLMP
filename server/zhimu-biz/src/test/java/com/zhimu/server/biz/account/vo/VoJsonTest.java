package com.zhimu.server.biz.account.vo;

import com.zhimu.server.biz.account.convert.UserConvert;
import com.zhimu.server.biz.account.entity.User;
import com.zhimu.server.biz.account.test.AccountTestMvc;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** 校验与 App Gson snake_case 契约一致的关键字段。 */
class VoJsonTest {

    @Test
    void 恢复码VO输出is_regenerated() {
        RecoveryCodeVO vo = RecoveryCodeVO.builder().recoveryCodeId(501L).code("8KQ4T2MW7HXN3PDR")
                .generatedAt("2026-10-19 09:30:00").phoneMasked("138****6620").scene("REGISTER")
                .sceneText("注册").isRegenerated(false).build();
        JsonNode json = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(json.get("recovery_code_id").asLong()).isEqualTo(501L);
        assertThat(json.get("is_regenerated").asBoolean()).isFalse();
        assertThat(json.get("scene_text").asString()).isEqualTo("注册");
    }

    @Test
    void 注销预览输出can_cancel_block_reasons与空集合() {
        CancellationPreviewVO vo = CancellationPreviewVO.builder()
                .phoneMasked("138****6620").canCancel(false)
                .blockReasons(java.util.List.of("OWNED_FARM_ACTIVE"))
                .ownedFarms(java.util.List.of()).exitFarms(java.util.List.of())
                .transferableFarmCount(0).checklistVersion("a1b2c3d4e5f60718").build();
        JsonNode json = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(json.get("can_cancel").asBoolean()).isFalse();
        assertThat(json.get("block_reasons").toString()).isEqualTo("[\"OWNED_FARM_ACTIVE\"]");
        assertThat(json.get("owned_farms").isArray()).isTrue();
        assertThat(json.get("transferable_farm_count").asInt()).isZero();
    }

    @Test
    void 账号用户VO输出姓名首字与打码手机号() {
        User user = new User();
        user.setId(10001L);
        user.setName("王建国");
        user.setPhone("13800006620");
        AccountUserVO vo = UserConvert.toAccountUser(user);
        JsonNode json = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(json.get("user_id").asLong()).isEqualTo(10001L);
        assertThat(json.get("name_initial").asString()).isEqualTo("王");
        assertThat(json.get("phone_masked").asString()).isEqualTo("138****6620");
    }

    @Test
    void 账号用户VO输出avatar_url_没有头像时为null() {
        User user = new User();
        user.setId(10001L);
        user.setName("王建国");
        user.setPhone("13800006620");
        AccountUserVO vo = UserConvert.toAccountUser(user);

        JsonNode withoutAvatar = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(withoutAvatar.has("avatar_url")).isTrue();
        assertThat(withoutAvatar.get("avatar_url").isNull()).isTrue();

        vo.setAvatarUrl("https://files.test/avatar/900.jpg");
        JsonNode withAvatar = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(withAvatar.get("avatar_url").asString()).isEqualTo("https://files.test/avatar/900.jpg");
    }

    @Test
    void 空字段不输出_布尔计数必须有值() {
        AccountOverviewVO vo = AccountOverviewVO.builder().profileVersion(0).build();
        JsonNode json = AccountTestMvc.MAPPER.valueToTree(vo);
        assertThat(json.has("user")).isFalse();
        assertThat(json.has("change_request_pending_count")).isFalse();
        assertThat(json.get("profile_version").asInt()).isZero();
    }
}
