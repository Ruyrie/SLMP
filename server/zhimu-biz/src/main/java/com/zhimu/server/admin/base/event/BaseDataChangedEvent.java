package com.zhimu.server.admin.base.event;

/** 品类、品种、字典项写操作提交后发布；缓存监听器后续删除 dict:CATEGORY / dict:{dict_type}。 */
public record BaseDataChangedEvent(String cacheKey, String dictType) {

    public static BaseDataChangedEvent category() {
        return new BaseDataChangedEvent("dict:CATEGORY", null);
    }

    public static BaseDataChangedEvent dict(String dictType) {
        return new BaseDataChangedEvent("dict:" + dictType, dictType);
    }
}
