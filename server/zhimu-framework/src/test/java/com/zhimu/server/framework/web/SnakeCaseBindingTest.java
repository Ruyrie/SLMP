package com.zhimu.server.framework.web;

import com.zhimu.server.common.api.ApiResponse;
import com.zhimu.server.common.api.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SnakeCaseBindingTest {

    @Getter
    @Setter
    public static class RecordQuery extends PageQuery {
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime since;
        private LocalTime planTime;
        private String bizType;
        private List<Long> categoryIds;
        @Min(value = 1, message = "数量至少为 1")
        private Integer minQty;
        private Boolean isDone;
        private Integer recent7DoneDays;
    }

    @RestController
    public static class QueryController {

        @GetMapping("/api/v1/test/records")
        public ApiResponse<RecordQuery> list(@Valid RecordQuery query) {
            return ApiResponse.ok(query);
        }

        @GetMapping("/api/v1/test/mixed")
        public ApiResponse<Map<String, Object>> mixed(@RequestParam("farm_name") String farmName, RecordQuery query) {
            return ApiResponse.ok(Map.of("farm_name", farmName, "biz_type", String.valueOf(query.getBizType())));
        }
    }

    private final MockMvc mvc = TestMvc.builder(new QueryController()).build();

    private JsonNode call(String url, int expectedStatus) throws Exception {
        var result = mvc.perform(get(url)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return TestMvc.json(result.getResponse().getContentAsString());
    }

    @Test
    void snake_case参数绑定到驼峰属性() throws Exception {
        JsonNode data = call("/api/v1/test/records?start_date=2026-10-01&end_date=2026-10-19&biz_type=FEED"
                + "&category_ids=1,2,3&page=2&size=50&is_done=true&recent7_done_days=3"
                + "&since=2026-10-19 08:30&plan_time=07:30", 200).get("data");

        assertThat(data.get("start_date").asString()).isEqualTo("2026-10-01");
        assertThat(data.get("end_date").asString()).isEqualTo("2026-10-19");
        assertThat(data.get("biz_type").asString()).isEqualTo("FEED");
        assertThat(data.get("category_ids").toString()).isEqualTo("[1,2,3]");
        assertThat(data.get("page").asInt()).isEqualTo(2);
        assertThat(data.get("size").asInt()).isEqualTo(50);
        assertThat(data.get("is_done").asBoolean()).isTrue();
        assertThat(data.get("recent7_done_days").asInt()).isEqualTo(3);
        assertThat(data.get("since").asString()).isEqualTo("2026-10-19 08:30:00");
        assertThat(data.get("plan_time").asString()).isEqualTo("07:30");
    }

    @Test
    void 驼峰参数名照常可用_重复参数绑定为列表() throws Exception {
        JsonNode data = call("/api/v1/test/records?startDate=2026-10-02&bizType=STOCK&categoryIds=4&categoryIds=5", 200)
                .get("data");

        assertThat(data.get("start_date").asString()).isEqualTo("2026-10-02");
        assertThat(data.get("biz_type").asString()).isEqualTo("STOCK");
        assertThat(data.get("category_ids").toString()).isEqualTo("[4,5]");
    }

    @Test
    void 绑定后执行校验_字段名为snake_case() throws Exception {
        JsonNode body = call("/api/v1/test/records?min_qty=0&size=101", 400);

        assertThat(body.get("code").asInt()).isEqualTo(40001);
        assertThat(body.get("data").get("field_errors").toString())
                .contains("{\"field\":\"min_qty\",\"message\":\"数量至少为 1\"}")
                .contains("{\"field\":\"size\",\"message\":\"每页最多 100 条\"}");
    }

    @Test
    void 类型不匹配返回格式不正确() throws Exception {
        JsonNode body = call("/api/v1/test/records?start_date=2026/10/01", 400);

        assertThat(body.get("code").asInt()).isEqualTo(40001);
        assertThat(body.get("data").get("field_errors").toString())
                .contains("{\"field\":\"start_date\",\"message\":\"格式不正确\"}");
    }

    @Test
    void 与RequestParam混用() throws Exception {
        JsonNode data = call("/api/v1/test/mixed?farm_name=东山猪场&biz_type=FEED", 200).get("data");
        assertThat(data.get("farm_name").asString()).isEqualTo("东山猪场");
        assertThat(data.get("biz_type").asString()).isEqualTo("FEED");
    }
}
