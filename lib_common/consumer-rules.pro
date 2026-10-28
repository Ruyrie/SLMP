# lib_common 消费者规则（03 号文档 12.2 节）

# VO、Request：Gson 按字段名映射下划线 JSON，字段不能重命名（覆盖 lib_common 与各业务组件的 model.vo、model.request 包）
-keep class com.zhimu.slmp.**.model.vo.** { <fields>; <init>(...); }
-keep class com.zhimu.slmp.**.model.request.** { <fields>; <init>(...); }

# 表单草稿：各 ViewModel 的内部类 Draft / DraftData 用 Gson 存入 Room（t_form_draft），字段名就是 JSON 键，
# 不能改名或被优化掉，否则升级后读不回草稿（覆盖 herd、feed、health、stock 等组件）
-keepclassmembers class com.zhimu.slmp.**$Draft { <fields>; <init>(); }
-keepclassmembers class com.zhimu.slmp.**$DraftData { <fields>; <init>(); }

# 组件入口：BaseApplication 按类名反射创建，保留实现类与无参构造方法
-keep class * implements com.zhimu.slmp.common.service.IModuleEntry { <init>(); }
