package com.zhimu.server.framework.jackson;

import com.zhimu.server.common.api.ApiResponse;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZhimuJacksonTest {

    private final JsonMapper mapper = ZhimuJackson.newHttpMapper();

    @Getter
    @Setter
    static class SampleVO {
        private Long batchId;
        private Boolean isBreeding;
        private Integer recent7DoneDays;
        private BigDecimal amount;
        private LocalDateTime createdAt;
        private LocalDate bornDate;
        private LocalTime planTime;
        private YearMonth month;
        private String remark;
        private Map<String, Integer> tabCounts;
    }

    @Test
    void 命名_布尔is字段_带数字字段_Map键原样() throws Exception {
        SampleVO vo = new SampleVO();
        vo.setBatchId(1L);
        vo.setIsBreeding(true);
        vo.setRecent7DoneDays(5);
        Map<String, Integer> tabs = new LinkedHashMap<>();
        tabs.put("TODO_ALERT", 2);
        tabs.put("unreadCount", 3);
        vo.setTabCounts(tabs);

        String json = mapper.writeValueAsString(vo);

        assertThat(json).contains("\"batch_id\":1")
                .contains("\"is_breeding\":true")
                .contains("\"recent7_done_days\":5")
                .contains("\"tab_counts\":{\"TODO_ALERT\":2,\"unreadCount\":3}")
                .doesNotContain("remark");
    }

    @Test
    void 请求体同样按snake_case读取并忽略未知字段() throws Exception {
        String json = "{\"batch_id\":9,\"is_breeding\":false,\"recent7_done_days\":4,\"unknown_field\":1}";
        SampleVO vo = mapper.readValue(json, SampleVO.class);
        assertThat(vo.getBatchId()).isEqualTo(9L);
        assertThat(vo.getIsBreeding()).isFalse();
        assertThat(vo.getRecent7DoneDays()).isEqualTo(4);
    }

    @Test
    void 日期时间格式() throws Exception {
        SampleVO vo = new SampleVO();
        vo.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 5, 9));
        vo.setBornDate(LocalDate.of(2026, 10, 1));
        vo.setPlanTime(LocalTime.of(7, 30, 15));
        vo.setMonth(YearMonth.of(2026, 9));

        String json = mapper.writeValueAsString(vo);

        assertThat(json).contains("\"created_at\":\"2026-10-19 08:05:09\"")
                .contains("\"born_date\":\"2026-10-01\"")
                .contains("\"plan_time\":\"07:30\"")
                .contains("\"month\":\"2026-09\"");
    }

    @Test
    void 日期时间反序列化接受两种写法() throws Exception {
        SampleVO a = mapper.readValue("{\"created_at\":\"2026-10-19 08:05:09\",\"plan_time\":\"07:30\"}", SampleVO.class);
        SampleVO b = mapper.readValue("{\"created_at\":\"2026-10-19 08:05\",\"plan_time\":\"07:30:15\",\"born_date\":\"2026-10-01\",\"month\":\"2026-09\"}", SampleVO.class);

        assertThat(a.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 8, 5, 9));
        assertThat(a.getPlanTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(b.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 8, 5));
        assertThat(b.getPlanTime()).isEqualTo(LocalTime.of(7, 30, 15));
        assertThat(b.getBornDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(b.getMonth()).isEqualTo(YearMonth.of(2026, 9));
    }

    @Test
    void BigDecimal不用科学计数法() throws Exception {
        SampleVO vo = new SampleVO();
        vo.setAmount(new BigDecimal("1E+3"));
        assertThat(mapper.writeValueAsString(vo)).contains("\"amount\":1000");

        vo.setAmount(new BigDecimal("0.00000012"));
        assertThat(mapper.writeValueAsString(vo)).contains("\"amount\":0.00000012");
    }

    @Test
    void 统一响应按声明顺序输出() throws Exception {
        String json = mapper.writeValueAsString(ApiResponse.ok(Map.of("k", 1)));
        assertThat(json).startsWith("{\"code\":0,\"message\":\"ok\",\"data\":{\"k\":1}");
        assertThat(json).doesNotContain("trace_id").doesNotContain("success");
    }

    @Test
    void 缓存用mapper与HTTP规则一致() throws Exception {
        SampleVO vo = new SampleVO();
        vo.setIsBreeding(true);
        vo.setCreatedAt(LocalDateTime.of(2026, 10, 19, 8, 5, 9));
        String json = CacheJson.write(vo);
        assertThat(json).contains("\"is_breeding\":true").contains("\"created_at\":\"2026-10-19 08:05:09\"");
        assertThat(CacheJson.read(json, SampleVO.class).getCreatedAt()).isEqualTo(vo.getCreatedAt());
    }
}
