package com.zhimu.server.biz.finance.service;

import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收支分类与子类（API-E09–E13）。 */
public interface FinanceCategoryService {

    List<FinanceRecordVos.CategoryVO> list(String direction);

    FinanceRecordVos.SubcategoryVO add(FinanceRequests.SubcategoryRequest request, String idempotencyKey);

    FinanceRecordVos.SubcategoryVO rename(Long id, FinanceRequests.SubcategoryRequest request);

    void delete(Long id);

    FinanceRecordVos.DuplicateCheckVO duplicateCheck(String recordType, Long categoryId, BigDecimal amount,
                                                     LocalDate recordDate, Long excludeId);
}
