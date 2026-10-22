# lib_network 消费者规则（03 号文档 12.2 节）

# Gson 泛型：保留泛型签名、内部类与注解属性，保证 ApiResponse<T>、PageVO<T> 的泛型解析正确
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken

# 统一响应结构的字段按名称映射
-keep class com.zhimu.slmp.network.model.** { <fields>; <init>(...); }

# Retrofit 方法返回类型中的泛型（R8 全量模式下需要保留）
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Single
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Completable
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Observable
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Flowable
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Maybe
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class retrofit2.Call
