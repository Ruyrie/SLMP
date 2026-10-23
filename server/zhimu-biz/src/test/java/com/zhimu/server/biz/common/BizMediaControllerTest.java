package com.zhimu.server.biz.common;

import com.zhimu.server.biz.common.api.BizMediaService;
import com.zhimu.server.biz.common.controller.BizMediaController;
import com.zhimu.server.biz.common.entity.BizMedia;
import com.zhimu.server.biz.common.fixture.CommonFlowFixture;
import com.zhimu.server.biz.common.mapper.BizMediaMapper;
import com.zhimu.server.biz.common.service.impl.RecordAccessGuard;
import com.zhimu.server.biz.common.support.TestMvc;
import com.zhimu.server.common.context.FarmContext;
import com.zhimu.server.common.context.UserContext;
import com.zhimu.server.common.enums.ClientType;
import com.zhimu.server.common.enums.FarmRole;
import com.zhimu.server.common.enums.FarmStatus;
import com.zhimu.server.common.vo.MediaItemVO;
import com.zhimu.server.framework.security.cache.FarmMemberCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API-U08 记录的照片和视频列表（BizMediaListVO + MediaItemVO）。
 */
class BizMediaControllerTest {

    private final BizMediaService bizMediaService = mock(BizMediaService.class);
    private final BizMediaMapper bizMediaMapper = mock(BizMediaMapper.class);
    private final FarmMemberCache farmMemberCache = mock(FarmMemberCache.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestMvc.builder(new BizMediaController(bizMediaService,
                new RecordAccessGuard(bizMediaMapper, farmMemberCache))).build();
        UserContext.set(new UserContext.LoginUser(10001L, ClientType.APP_ANDROID, 0, "jti"));
        when(bizMediaMapper.selectActiveByBiz(anyString(), any())).thenReturn(List.of(media(1001L)));
        when(farmMemberCache.get(1001L, 10001L)).thenReturn(new FarmContext.FarmMember(1001L, "东山猪场",
                10001L, 5001L, FarmRole.OWNER, Set.of(), FarmStatus.NORMAL));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        FarmContext.clear();
    }

    @Test
    void returnsBizMediaListWithItems() throws Exception {
        MediaItemVO item = new MediaItemVO();
        item.setFileId(501L);
        item.setUrl("https://files.test/inspection/1.jpg");
        item.setMediaType("IMAGE");
        item.setMimeType("image/jpeg");
        item.setSize(2048L);
        item.setStatus("READY");
        item.setSortNo(1);
        item.setSourceText("巡检");
        item.setShotBy("李明");
        item.setShotAt(LocalDateTime.of(2026, 10, 19, 9, 10));
        when(bizMediaService.list(com.zhimu.server.common.enums.BizType.INSPECTION, 7001L)).thenReturn(List.of(item));

        mvc.perform(get("/api/v1/biz-media").param("biz_type", "INSPECTION").param("biz_id", "7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.biz_type").value("INSPECTION"))
                .andExpect(jsonPath("$.data.biz_type_text").value("巡检记录"))
                .andExpect(jsonPath("$.data.record_desc").value("巡检记录 · 7001"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].file_id").value(501))
                .andExpect(jsonPath("$.data.items[0].sort_no").value(1))
                .andExpect(jsonPath("$.data.items[0].source_text").value("巡检"))
                .andExpect(jsonPath("$.data.items[0].shot_by").value("李明"))
                .andExpect(jsonPath("$.data.items[0].shot_at").value("2026-10-19 09:10:00"))
                .andExpect(jsonPath("$.data.items[0].media_type").value("IMAGE"));
    }

    @Test
    void rejectsRecordOfOtherFarm() throws Exception {
        when(bizMediaMapper.selectActiveByBiz(anyString(), any())).thenReturn(List.of(media(1002L)));
        mvc.perform(get("/api/v1/biz-media").param("biz_type", "INSPECTION").param("biz_id", "7001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void rejectsUnknownBizType() throws Exception {
        mvc.perform(get("/api/v1/biz-media").param("biz_type", "NOT_A_TYPE").param("biz_id", "7001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.field_errors[0].field").value("biz_type"));
    }

    private static BizMedia media(Long farmId) {
        BizMedia row = new BizMedia();
        row.setId(1L);
        row.setBizType("INSPECTION");
        row.setBizId(7001L);
        row.setFileId(501L);
        row.setFarmId(farmId);
        row.setSortNo(1);
        row.setDeleted(0);
        return row;
    }
}
