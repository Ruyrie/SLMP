package com.zhimu.server.admin.system.service.impl;

import com.zhimu.server.admin.system.mapper.AdminMapper;
import com.zhimu.server.admin.system.mapper.readonly.AdminOperationLogQueryMapper;
import com.zhimu.server.admin.system.vo.OperationLogDetailVO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 操作日志“打开对应记录”：页面编号与定位参数。 */
class OperationLogLinkTest {

    private final AdminOperationLogQueryMapper queryMapper = mock(AdminOperationLogQueryMapper.class);
    private final OperationLogAdminServiceImpl service =
            new OperationLogAdminServiceImpl(queryMapper, mock(AdminMapper.class));

    @Test
    void 养殖场日志的bizId是养殖场ID_定位到养殖场管理() {
        OperationLogDetailVO.Link link = service.link("FARM", 1001L);
        assertThat(link.getPageCode()).isEqualTo("WA-S04");
        assertThat(link.getExists()).isTrue();
        assertThat(link.getQuery()).isEqualTo(Map.of("id", "1001"));
    }

    @Test
    void 标准版本按品种定位_版本不存在时不可打开() {
        when(queryMapper.selectStdWeightBreedId(5L)).thenReturn(1101L);
        OperationLogDetailVO.Link link = service.link("STD_WEIGHT", 5L);
        assertThat(link.getPageCode()).isEqualTo("WA-D01");
        assertThat(link.getQuery()).containsEntry("breed_id", "1101").containsEntry("tab", "weight");

        when(queryMapper.selectFeedStdBreedId(6L)).thenReturn(null);
        OperationLogDetailVO.Link missing = service.link("FEED_STD", 6L);
        assertThat(missing.getExists()).isFalse();
        assertThat(missing.getQuery()).isNull();
    }

    @Test
    void 字典项与收支分类带字典类型() {
        when(queryMapper.selectDictItemType(301L)).thenReturn("DEATH_REASON");
        when(queryMapper.selectFinanceCategoryDirection(101L)).thenReturn("EXPENSE");
        assertThat(service.link("DICT_ITEM", 301L).getQuery())
                .containsEntry("dict_type", "DEATH_REASON").containsEntry("id", "301");
        assertThat(service.link("FINANCE_CATEGORY", 101L).getQuery())
                .containsEntry("dict_type", "EXPENSE_CATEGORY").containsEntry("id", "101");
    }

    @Test
    void 课程任务角色禁用药各自的参数() {
        when(queryMapper.selectBannedDrugName(9L)).thenReturn("克伦特罗");
        assertThat(service.link("COURSE", 7L).getQuery()).isEqualTo(Map.of("course_id", "7"));
        assertThat(service.link("KNOWLEDGE_TASK", 8L).getQuery()).isEqualTo(Map.of("task_id", "8"));
        assertThat(service.link("ADMIN_ROLE", 2L).getQuery()).containsEntry("tab", "roles").containsEntry("role_id", "2");
        assertThat(service.link("BANNED_DRUG", 9L).getQuery()).containsEntry("tab", "BANNED").containsEntry("keyword", "克伦特罗");
        assertThat(service.link("AI_MESSAGE", 11L).getPageCode()).isEqualTo("WA-C05");
    }

    @Test
    void 筛选基础数据与冻结时包含实际写入的op_type_且显示中文类别() {
        assertThat(com.zhimu.server.admin.system.support.AdminLogTexts.expandOpType("BASE_DATA"))
                .contains("BASE_DICT", "BASE_BREED", "BASE_IMMUNE_TEMPLATE");
        assertThat(com.zhimu.server.admin.system.support.AdminLogTexts.expandOpType("FARM_FREEZE"))
                .containsExactly("FARM_FREEZE", "FARM");
        assertThat(com.zhimu.server.admin.system.support.AdminLogTexts.expandOpType(" ")).isNull();
        assertThat(com.zhimu.server.admin.system.support.AdminLogTexts.opTypeText("BASE_DICT")).isEqualTo("基础数据");
        assertThat(com.zhimu.server.admin.system.support.AdminLogTexts.opTypeText("FARM")).isEqualTo("养殖场冻结 / 解冻");
    }

    @Test
    void 没有页面能定位的类型不显示按钮() {
        OperationLogDetailVO.Link link = service.link("FILE", 1L);
        assertThat(link.getPageCode()).isNull();
        assertThat(link.getExists()).isFalse();
        assertThat(service.link(null, null).getExists()).isFalse();
    }
}
