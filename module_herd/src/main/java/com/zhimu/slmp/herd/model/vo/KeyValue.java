package com.zhimu.slmp.herd.model.vo;

/**
 * 键值对（时间线展开详情“登记人 李明”）。文档列为公共类型，但 lib_common 尚未提供，先在本组件内定义，
 * JSON 字段暂按 key、value（交付说明“待确认”）。
 */
public class KeyValue {

    private String key;
    private String value;

    public String getKey() { return key; }

    public String getValue() { return value; }
}
