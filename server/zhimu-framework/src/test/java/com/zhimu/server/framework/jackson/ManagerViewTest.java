package com.zhimu.server.framework.jackson;

import com.zhimu.server.common.annotation.ManagerView;
import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageVO;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ManagerViewTest {

    /** 同一个 mapper 反复使用，验证序列化器缓存后仍按每次请求的角色裁剪 */
    private static final JsonMapper MAPPER = ZhimuJackson.newHttpMapper();

    @Getter
    @Setter
    static class SaleVO {
        private Long id;
        private BigDecimal qty;
        @ManagerView
        private BigDecimal unitPrice;
        @ManagerView
        private BigDecimal amount;
    }

    @AfterEach
    void tearDown() {
        FarmContext.clear();
    }

    private static SaleVO sample() {
        SaleVO vo = new SaleVO();
        vo.setId(1L);
        vo.setQty(new BigDecimal("10.5"));
        vo.setUnitPrice(new BigDecimal("12.30"));
        vo.setAmount(new BigDecimal("129.15"));
        return vo;
    }

    private static void asRole(FarmRole role) {
        FarmContext.set(new FarmContext.FarmMember(1001L, "东山猪场", 1L, 1L, role, Set.of(), FarmStatus.NORMAL));
    }

    @Test
    void 场长能看到管理视图字段() throws Exception {
        asRole(FarmRole.MANAGER);
        String json = MAPPER.writeValueAsString(sample());
        assertThat(json).contains("\"unit_price\":12.30").contains("\"amount\":129.15");
    }

    @Test
    void 员工看不到管理视图字段_键也不存在() throws Exception {
        // 先以所有者身份序列化一次，让序列化器进入缓存
        asRole(FarmRole.OWNER);
        assertThat(MAPPER.writeValueAsString(sample())).contains("unit_price");

        asRole(FarmRole.STAFF);
        String json = MAPPER.writeValueAsString(ApiResponse.ok(PageVO.of(List.of(sample()), 1, 1, 20)));
        assertThat(json).contains("\"qty\":10.5").doesNotContain("unit_price").doesNotContain("amount");

        asRole(FarmRole.MANAGER);
        assertThat(MAPPER.writeValueAsString(sample())).contains("unit_price").contains("amount");
    }

    @Test
    void 没有养殖场上下文时按非员工输出() throws Exception {
        String json = MAPPER.writeValueAsString(sample());
        assertThat(json).contains("unit_price").contains("amount");
    }

    @Test
    void 缓存用mapper不裁剪() throws Exception {
        asRole(FarmRole.STAFF);
        assertThat(CacheJson.write(sample())).contains("unit_price");
    }
}
