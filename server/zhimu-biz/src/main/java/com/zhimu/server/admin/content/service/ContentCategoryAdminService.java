package com.zhimu.server.admin.content.service;

import com.zhimu.server.admin.content.request.CategoryOrderRequest;
import com.zhimu.server.admin.content.request.ContentCategorySaveRequest;
import com.zhimu.server.admin.content.vo.ContentCategoryVO;
import com.zhimu.server.biz.ai.entity.CourseCategory;

import java.util.List;

/**
 * 分类（ADM-C40–C43）：同时服务课程与知识库，WA-C03 或 WA-C04 任一处理权限都可维护。
 */
public interface ContentCategoryAdminService {

    List<ContentCategoryVO> list(String status);

    ContentCategoryVO create(ContentCategorySaveRequest request);

    ContentCategoryVO update(Long id, ContentCategorySaveRequest request);

    List<ContentCategoryVO> reorder(CategoryOrderRequest request);

    /** 启用中的分类；不存在或已停用抛 40001“请选择分类” */
    CourseCategory requireEnabled(Long id);

    String nameOf(Long id);

    /** 同步分类名到课程、专题、知识条目的冗余字段 */
    void rename(Long categoryId, String name);
}
