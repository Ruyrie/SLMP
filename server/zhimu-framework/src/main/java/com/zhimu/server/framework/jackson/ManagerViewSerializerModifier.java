package com.zhimu.server.framework.jackson;

import com.zhimu.server.common.annotation.ManagerView;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.List;

/**
 * 把标了 @ManagerView 的属性换成 {@link ManagerViewPropertyWriter}：序列化器按类型缓存，
 * 因此不在这里移除字段，而是在每次输出时按当前养殖场角色决定是否跳过。
 */
public class ManagerViewSerializerModifier extends ValueSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription.Supplier beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (int i = 0; i < beanProperties.size(); i++) {
            BeanPropertyWriter writer = beanProperties.get(i);
            if (writer.getAnnotation(ManagerView.class) != null && !(writer instanceof ManagerViewPropertyWriter)) {
                beanProperties.set(i, new ManagerViewPropertyWriter(writer));
            }
        }
        return beanProperties;
    }
}
