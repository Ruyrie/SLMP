package com.zhimu.server.admin.base.service.impl;

import com.zhimu.server.admin.base.AdminBaseTableInfo;
import com.zhimu.server.admin.base.mapper.BaseAdminReadMapper;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.support.BaseLogs;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.common.context.AdminContext;
import com.zhimu.server.common.error.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** WA-D05 收入 / 支出分类：编码取号、重名、兜底与自动记账分类不能停用。 */
class FinanceCategoryDictSupportTest {

    private final FinanceCategoryMapper mapper = mock(FinanceCategoryMapper.class);
    private final BaseAdminReadMapper readMapper = mock(BaseAdminReadMapper.class);
    private final FinanceCategoryDictSupport support = new FinanceCategoryDictSupport(mapper, readMapper,
            mock(BaseLogs.class), mock(ApplicationEventPublisher.class));

    @BeforeEach
    void setUp() {
        AdminBaseTableInfo.ensure();
        AdminContext.set(new AdminContext.LoginAdmin(1L, "周敏", Set.of("SUPER_ADMIN"), Set.of(), 0, "t"));
    }

    @AfterEach
    void tearDown() {
        AdminContext.clear();
    }

    private static FinanceCategory category(long id, String direction, String code, String name, int sort,
                                            String autoSource, boolean fallback) {
        FinanceCategory category = new FinanceCategory();
        category.setId(id);
        category.setDirection(direction);
        category.setCode(code);
        category.setName(name);
        category.setSortNo(sort);
        category.setStatus("ENABLED");
        category.setAutoSource(autoSource);
        category.setIsFallback(fallback);
        category.setVersion(0);
        return category;
    }

    @Test
    void 新增支出分类_编码按EXP加序号_排序接在后面_重名拒绝() {
        List<FinanceCategory> expense = List.of(
                category(101, "EXPENSE", "FEED", "饲料采购", 1, "FEED_INBOUND", false),
                category(107, "EXPENSE", "OTHER", "其他", 7, null, true),
                category(108, "EXPENSE", "EXP_002", "保险", 8, null, false));
        when(mapper.selectList(any())).thenReturn(expense);
        when(mapper.selectList(isNull())).thenReturn(expense);
        DictItemSaveRequest request = new DictItemSaveRequest();
        request.setName("运输费");

        DictItemVO vo = support.create("EXPENSE_CATEGORY", request);

        ArgumentCaptor<FinanceCategory> captor = ArgumentCaptor.forClass(FinanceCategory.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("EXP_003");
        assertThat(captor.getValue().getDirection()).isEqualTo("EXPENSE");
        assertThat(captor.getValue().getSortNo()).isEqualTo(9);
        assertThat(captor.getValue().getStatus()).isEqualTo("ENABLED");
        assertThat(vo.getName()).isEqualTo("运输费");

        request.setName("保险");
        assertThatThrownBy(() -> support.create("EXPENSE_CATEGORY", request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(40905));
    }

    @Test
    void 兜底与自动记账分类不能停用() {
        when(mapper.selectById(107L)).thenReturn(category(107, "EXPENSE", "OTHER", "其他", 7, null, true));
        when(mapper.selectById(101L)).thenReturn(category(101, "EXPENSE", "FEED", "饲料采购", 1, "FEED_INBOUND", false));
        DictStatusRequest request = new DictStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("不用了");
        request.setVersion(0);

        assertThatThrownBy(() -> support.changeStatus("EXPENSE_CATEGORY", 107L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(42207));
        assertThatThrownBy(() -> support.changeStatus("EXPENSE_CATEGORY", 101L, request))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getCode()).isEqualTo(42207));
        // 方向不对按不存在处理
        assertThatThrownBy(() -> support.changeStatus("INCOME_CATEGORY", 101L, request))
                .isInstanceOf(BizException.class);
    }
}
