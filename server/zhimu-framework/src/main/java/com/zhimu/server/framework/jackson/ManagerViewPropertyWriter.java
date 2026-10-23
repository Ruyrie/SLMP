package com.zhimu.server.framework.jackson;

import com.zhimu.server.common.context.FarmContext;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

/**
 * 管理视图字段：当前请求是员工（FarmContext.isStaff()）时整个键都不输出。
 */
public class ManagerViewPropertyWriter extends BeanPropertyWriter {

    public ManagerViewPropertyWriter(BeanPropertyWriter base) {
        super(base);
    }

    protected ManagerViewPropertyWriter(BeanPropertyWriter base, PropertyName name) {
        super(base, name);
    }

    @Override
    protected BeanPropertyWriter _new(PropertyName newName) {
        return new ManagerViewPropertyWriter(this, newName);
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception {
        if (FarmContext.isStaff()) {
            return;
        }
        super.serializeAsProperty(bean, g, ctxt);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception {
        if (FarmContext.isStaff()) {
            serializeAsOmittedElement(bean, g, ctxt);
            return;
        }
        super.serializeAsElement(bean, g, ctxt);
    }
}
