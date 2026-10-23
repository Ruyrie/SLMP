package com.zhimu.server.admin.base.controller;

import com.zhimu.server.admin.base.AdminBaseTestMvc;
import com.zhimu.server.admin.base.service.DictAdminService;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.admin.base.vo.DictTypeVO;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADM-D49–D54 字典导航、列表、增改、排序、启停与错误分支。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DictAdminControllerMockMvcTest {

    @Mock private DictAdminService dictService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = AdminBaseTestMvc.builder(new DictAdminController(dictService)).build();
    }

    @Test
    void d49ReturnsEightDictTypes() throws Exception {
        DictTypeVO type = new DictTypeVO();
        type.setDictType("PEN_TYPE");
        type.setName("栏舍类型");
        type.setItemCount(16);
        type.setScopeMode("SPECIES");
        type.setPlatformOnly(false);
        when(dictService.types()).thenReturn(List.of(type));

        mvc.perform(get("/admin/v1/dicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dict_type").value("PEN_TYPE"))
                .andExpect(jsonPath("$.data[0].scope_mode").value("SPECIES"))
                .andExpect(jsonPath("$.data[0].platform_only").value(false));
    }

    @Test
    void d50ToD54Routes() throws Exception {
        DictItemVO item = new DictItemVO();
        item.setId(501L);
        item.setCode("NURSERY");
        item.setName("保育舍");
        item.setVersion(2);
        when(dictService.items(eq("PEN_TYPE"), any())).thenReturn(List.of(item));
        when(dictService.create(eq("PEN_TYPE"), any())).thenReturn(item);
        when(dictService.update(eq("PEN_TYPE"), eq(501L), any())).thenReturn(item);
        DictItemVO disabled = new DictItemVO();
        disabled.setId(501L);
        disabled.setCode("NURSERY");
        disabled.setName("保育舍");
        disabled.setStatus("DISABLED");
        disabled.setVersion(3);
        when(dictService.changeStatus(eq("PEN_TYPE"), eq(501L), any())).thenReturn(disabled);

        mvc.perform(get("/admin/v1/dicts/PEN_TYPE/items").param("species", "PIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(501));

        mvc.perform(post("/admin/v1/dicts/PEN_TYPE/items").header("Idempotency-Key", "d51")
                        .contentType("application/json")
                        .content("{\"name\":\"新栏舍\",\"scopes\":[{\"scope_type\":\"SPECIES\",\"species\":\"PIG\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已保存"));

        mvc.perform(put("/admin/v1/dicts/PEN_TYPE/items/501").contentType("application/json")
                        .content("{\"name\":\"保育舍\",\"version\":2,"
                                + "\"scopes\":[{\"scope_type\":\"SPECIES\",\"species\":\"PIG\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(501));

        mvc.perform(put("/admin/v1/dicts/PEN_TYPE/items/order").contentType("application/json")
                        .content("{\"item_ids\":[501,502]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已调整顺序"));

        mvc.perform(post("/admin/v1/dicts/PEN_TYPE/items/501/status").contentType("application/json")
                        .content("{\"status\":\"DISABLED\",\"reason\":\"合并\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("已停用“保育舍”"));
    }

    @Test
    void fallbackOrSystemDisableReturns42207() throws Exception {
        when(dictService.changeStatus(eq("DEATH_REASON"), eq(604L), any()))
                .thenThrow(new BizException(ErrorCode.STATE_NOT_ALLOWED, "‘其他’是兜底选项，不能停用"));
        mvc.perform(post("/admin/v1/dicts/DEATH_REASON/items/604/status").contentType("application/json")
                        .content("{\"status\":\"DISABLED\",\"reason\":\"x\",\"version\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42207))
                .andExpect(jsonPath("$.message").value("‘其他’是兜底选项，不能停用"));
    }

    @Test
    void invalidDictTypeReturns40401() throws Exception {
        when(dictService.items(eq("NOPE"), any())).thenThrow(new BizException(ErrorCode.NOT_FOUND));
        mvc.perform(get("/admin/v1/dicts/NOPE/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
