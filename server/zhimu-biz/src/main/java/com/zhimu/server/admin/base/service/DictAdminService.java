package com.zhimu.server.admin.base.service;

import com.zhimu.server.admin.base.query.DictItemQuery;
import com.zhimu.server.admin.base.request.DictItemSaveRequest;
import com.zhimu.server.admin.base.request.DictStatusRequest;
import com.zhimu.server.admin.base.request.ItemOrderRequest;
import com.zhimu.server.admin.base.vo.DictItemVO;
import com.zhimu.server.admin.base.vo.DictTypeVO;

import java.util.List;

/** ADM-D49–D54 字典导航、项列表、增改、排序、启停（本批 8 类，财务分类待模块10）。 */
public interface DictAdminService {

    List<DictTypeVO> types();

    List<DictItemVO> items(String dictType, DictItemQuery query);

    DictItemVO create(String dictType, DictItemSaveRequest request);

    DictItemVO update(String dictType, Long id, DictItemSaveRequest request);

    void order(String dictType, ItemOrderRequest request);

    DictItemVO changeStatus(String dictType, Long id, DictStatusRequest request);
}
