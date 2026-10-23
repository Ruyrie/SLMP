package com.zhimu.server.biz.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhimu.server.biz.finance.entity.ExpenseSubcategory;
import com.zhimu.server.biz.finance.entity.FinanceCategory;
import com.zhimu.server.biz.finance.entity.FinanceRecord;
import com.zhimu.server.biz.finance.mapper.ExpenseSubcategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceCategoryMapper;
import com.zhimu.server.biz.finance.mapper.FinanceRecordMapper;
import com.zhimu.server.biz.finance.request.FinanceRequests;
import com.zhimu.server.biz.finance.service.FinanceCategoryService;
import com.zhimu.server.biz.finance.support.FinanceConst;
import com.zhimu.server.biz.finance.support.FinanceErrors;
import com.zhimu.server.biz.finance.support.FinanceSupport;
import com.zhimu.server.biz.finance.vo.FinanceRecordVos;
import com.zhimu.server.common.error.BizException;
import com.zhimu.server.common.error.ErrorCode;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** API-E09 疑似重复、E10 分类、E11–E13 子类。 */
@Service
@RequiredArgsConstructor
public class FinanceCategoryServiceImpl implements FinanceCategoryService {

    private final FinanceCategoryMapper categoryMapper;
    private final ExpenseSubcategoryMapper subcategoryMapper;
    private final FinanceRecordMapper recordMapper;
    private final FinanceSupport support;

    @Override
    public List<FinanceRecordVos.CategoryVO> list(String direction) {
        Long farmId = support.farmId();
        List<FinanceCategory> categories = categoryMapper.selectList(Wrappers.<FinanceCategory>lambdaQuery()
                .eq(FinanceCategory::getStatus, "ENABLED")
                .eq(direction != null && !direction.isBlank(), FinanceCategory::getDirection, direction)
                .orderByAsc(FinanceCategory::getDirection)
                .orderByAsc(FinanceCategory::getSortNo));
        Map<Long, List<ExpenseSubcategory>> subs = subcategoryMapper.selectList(
                        Wrappers.<ExpenseSubcategory>lambdaQuery()
                                .eq(ExpenseSubcategory::getFarmId, farmId)
                                .orderByAsc(ExpenseSubcategory::getCategoryId)
                                .orderByAsc(ExpenseSubcategory::getId))
                .stream().collect(Collectors.groupingBy(ExpenseSubcategory::getCategoryId, LinkedHashMap::new,
                        Collectors.toList()));
        List<FinanceRecordVos.CategoryVO> result = new ArrayList<>();
        for (FinanceCategory category : categories) {
            FinanceRecordVos.CategoryVO vo = new FinanceRecordVos.CategoryVO();
            vo.setId(category.getId());
            vo.setCode(category.getCode());
            vo.setName(category.getName());
            vo.setIcon(category.getIcon());
            vo.setDirection(category.getDirection());
            vo.setSortNo(category.getSortNo());
            vo.setAutoSource(category.getAutoSource() != null);
            vo.setMarkOnly(Boolean.TRUE.equals(category.getMarkOnly()));
            vo.setIsFallback(Boolean.TRUE.equals(category.getIsFallback()));
            for (ExpenseSubcategory sub : subs.getOrDefault(category.getId(), List.of())) {
                FinanceRecordVos.SubcategoryVO subVo = new FinanceRecordVos.SubcategoryVO();
                subVo.setId(sub.getId());
                subVo.setName(sub.getName());
                subVo.setCreatedAt(FinanceSupport.format(sub.getCreatedAt()));
                vo.getSubcategories().add(subVo);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public FinanceRecordVos.SubcategoryVO add(FinanceRequests.SubcategoryRequest request, String idempotencyKey) {
        Long farmId = support.farmId();
        FinanceCategory category = requireExpenseCategory(request.getCategoryId());
        String name = validName(request.getName());
        requireNotDuplicate(farmId, category, name, null);
        long count = subcategoryMapper.selectCount(Wrappers.<ExpenseSubcategory>lambdaQuery()
                .eq(ExpenseSubcategory::getFarmId, farmId)
                .eq(ExpenseSubcategory::getCategoryId, category.getId()));
        FinanceErrors.require(count < 30, "name", "每个分类最多添加 30 个子类");
        ExpenseSubcategory sub = new ExpenseSubcategory();
        sub.setFarmId(farmId);
        sub.setCategoryId(category.getId());
        sub.setName(name);
        subcategoryMapper.insert(sub);
        return toVo(sub);
    }

    @Override
    public FinanceRecordVos.SubcategoryVO rename(Long id, FinanceRequests.SubcategoryRequest request) {
        Long farmId = support.farmId();
        ExpenseSubcategory sub = subcategoryMapper.selectById(id);
        if (sub == null || !farmId.equals(sub.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        FinanceCategory category = categoryMapper.selectById(sub.getCategoryId());
        String name = validName(request.getName());
        requireNotDuplicate(farmId, category, name, id);
        sub.setName(name);
        subcategoryMapper.updateById(sub);
        return toVo(sub);
    }

    @Override
    public void delete(Long id) {
        Long farmId = support.farmId();
        ExpenseSubcategory sub = subcategoryMapper.selectById(id);
        if (sub == null || !farmId.equals(sub.getFarmId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        sub.setDeletedAt(BeijingTime.now());
        subcategoryMapper.updateById(sub);
        subcategoryMapper.deleteById(id);
    }

    @Override
    public FinanceRecordVos.DuplicateCheckVO duplicateCheck(String recordType, Long categoryId, BigDecimal amount,
                                                            LocalDate recordDate, Long excludeId) {
        FinanceRecordVos.DuplicateCheckVO vo = new FinanceRecordVos.DuplicateCheckVO();
        vo.setHit(false);
        FinanceCategory category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null || !List.of("FEED", "DRUG", "SALE").contains(category.getCode())) {
            return vo;
        }
        Long farmId = support.farmId();
        FinanceRecord hit = recordMapper.selectOne(Wrappers.<FinanceRecord>lambdaQuery()
                .eq(FinanceRecord::getFarmId, farmId)
                .eq(FinanceRecord::getRecordDate, recordDate)
                .eq(FinanceRecord::getAmount, amount)
                .eq(FinanceRecord::getCategoryId, categoryId)
                .eq(FinanceRecord::getStatus, FinanceConst.RECORD_NORMAL)
                .ne(FinanceRecord::getSourceType, FinanceConst.SOURCE_MANUAL)
                .ne(excludeId != null, FinanceRecord::getId, excludeId)
                .last("LIMIT 1"));
        if (hit == null) {
            return vo;
        }
        vo.setHit(true);
        vo.setRecordId(hit.getId());
        vo.setCategoryName(category.getName());
        vo.setAmount(hit.getAmount());
        vo.setRecordDate(FinanceSupport.format(hit.getRecordDate()));
        vo.setSourceLabel(FinanceConst.sourceLabel(hit.getSourceType()));
        return vo;
    }

    private FinanceCategory requireExpenseCategory(Long categoryId) {
        FinanceErrors.require(categoryId != null, "category_id", "请选择分类");
        FinanceCategory category = categoryMapper.selectById(categoryId);
        if (category == null || !FinanceConst.EXPENSE.equals(category.getDirection())) {
            throw FinanceErrors.field("category_id", "请选择分类");
        }
        if (!"ENABLED".equals(category.getStatus())) {
            throw FinanceErrors.field("category_id", "该分类已停用，请重新选择");
        }
        return category;
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        FinanceErrors.require(!name.isEmpty(), "name", "请输入子类名称");
        FinanceErrors.require(name.codePointCount(0, name.length()) <= 10, "name", "子类名称不能超过 10 个字");
        return name;
    }

    private void requireNotDuplicate(Long farmId, FinanceCategory category, String name, Long excludeId) {
        ExpenseSubcategory exists = subcategoryMapper.selectOne(Wrappers.<ExpenseSubcategory>lambdaQuery()
                .eq(ExpenseSubcategory::getFarmId, farmId)
                .eq(ExpenseSubcategory::getCategoryId, category.getId())
                .eq(ExpenseSubcategory::getName, name)
                .ne(excludeId != null, ExpenseSubcategory::getId, excludeId)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BizException(ErrorCode.NAME_DUPLICATE,
                    category.getName() + "下已有“" + name + "”");
        }
    }

    private static FinanceRecordVos.SubcategoryVO toVo(ExpenseSubcategory sub) {
        FinanceRecordVos.SubcategoryVO vo = new FinanceRecordVos.SubcategoryVO();
        vo.setId(sub.getId());
        vo.setName(sub.getName());
        vo.setCreatedAt(FinanceSupport.format(sub.getCreatedAt()));
        return vo;
    }
}
