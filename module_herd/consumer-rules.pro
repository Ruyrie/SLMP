# module_herd 消费者规则：model.vo、model.request 的字段由 lib_common 的 consumer-rules 统一保留
# （com.zhimu.slmp.**.model.vo.**、model.request.**），HerdModuleEntry 的无参构造方法同样由 lib_common 保留；
# ZXing 不使用反射，无需手写规则。
