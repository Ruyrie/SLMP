package com.zhimu.server.admin.base.api;

import com.zhimu.server.admin.base.vo.BaseDictItemVO;

import java.util.List;

/** 字典查询（U10 与各业务模块按 code 取名称）。 */
public interface DictQueryService {

    /**
     * API-U10：只返回启用项。
     *
     * @param dictType   8 类字典之一
     * @param species    可选，按畜种过滤
     * @param categoryId 可选，按品类过滤（优先于 species）
     * @param farmId     可选，未传 species/category_id 时 PEN_TYPE / VACCINATE_METHOD 按该场主要品类推断畜种
     */
    List<BaseDictItemVO> listForApp(String dictType, String species, Long categoryId, Long farmId);

    /** 同类型全部项（含停用），供缓存与按 code 取名称 */
    List<DictEntry> listAll(String dictType);

    /** 按 code 取名称；停用项也能取到名称，不存在返回 null */
    String nameOf(String dictType, String code);

    /** 字典项全量信息（不含适用范围） */
    record DictEntry(Long id, String dictType, String code, String name, String description, boolean remarkRequired,
                     boolean isSystem, int sortNo, boolean enabled) {
    }
}
