package com.zhimu.server.admin.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典项适用范围（t_dict_item_scope）：按畜种或按品类；没有记录表示全部品类。
 * 可空列约定为 species=''、category_id=0，避免 NULL 使唯一索引失效。
 */
@Getter
@Setter
@TableName("t_dict_item_scope")
public class DictItemScope extends AdminDetailEntity {

    private Long dictItemId;
    /** DICT_SCOPE：SPECIES / CATEGORY */
    private String scopeType;
    private String species;
    private Long categoryId;
}
